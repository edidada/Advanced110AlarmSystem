# Seata 应用与分库分表说明（2026-11-01）

> 面向对象：Advanced110AlarmSystem 维护者。说明三件事——**为什么要引入 Seata + ShardingSphere**、
> 在本项目里**怎么落地**（代码/配置/SQL/Docker/k8s）、**当前集群真实状态**与运维路径。
> 组件版本固定：`org.apache.shardingsphere:*:5.5.2` + `org.apache.seata:seata-all:2.2.0` + `apache/seata-server:2.2.0`（TC）。

## 一、结论速览

| 问题 | 答案 |
|---|---|
| Seata 解决什么 | 分库后"一次业务写横跨多个 MySQL 物理库、任何一个失败要全部回滚"的**跨库（跨分片）事务一致性** |
| 分库分表怎么分 | `incident` 按主键 `id`(UUID) 哈希取模 2 → `alarm110_0`/`alarm110_1`；幂等/Outbox/审计/警力/派遣留在全局 `alarm110_sys` |
| 应用侧观感 | 业务代码**零改动**：仓储仍是普通 JdbcTemplate，事务仍是 Spring `@Transactional`（官方允许）；只多了 `sharding` profile |
| 上线开关 | 环境变量三件套：`SPRING_PROFILES_ACTIVE=k8s,sharding` + `ALARM_SEATA_ENABLED=true`（不开 Seata 时分片照常、事务=LOCAL） |
| 本地/单测 | 默认 profile 完全不变（H2 单库），新路由回归测试走本地 H2 三库模拟 |

## 二、为什么是"警单分片 + 全局单表"这个形式

### 2.1 数据特征

- `incident`（警单）是唯一预期无限增长、且写读都带天然主键定位的大表（报警在增长，历史警单永久查询）；
- `idempotency_record`、`outbox_event` 的**唯一约束是业务正确性的生命线**（幂等键全局唯一、Outbox event_id 全局唯一），
  一旦"唯一约束跟随分片"，跨分片撞键行为在 MySQL 里不可见（每个分片各自接受同样的 key）→ 不能分；
- `unit_status`/`dispatch_task` 是**活跃场**（在册警力和活动派遣量与队伍规模同阶的常数级），分片收益 ≈ 0。

因此：只把**增长快、可按主键路由**的 `incident` 分片；约束语义必须全局的表留在单一 `sys` 库。

### 2.2 事务为什么需要 Seata（AT）

一次 `POST /incidents` 现在的物理写：

1. `incident` INSERT → 命中 `alarm110_0` **或** `alarm110_1`（hash 分片）；
2. `idempotency_record` INSERT + `markCompleted` → `alarm110_sys`；
3. `outbox_event` INSERT → `alarm110_sys`。

三步原本在一个 Spring 事务里；分库后横跨两个物理库，`LOCAL` 模式只保证单库原子性——
**任意一步崩溃后，另一库可能留下"已写 outbox 但没有警单"或反向的孤儿数据**。
Seata AT 用"一阶段业务 SQL + 自动快照（undo_log），二阶段并发提交/按快照回滚"把这两个库纳入同一个全局事务。

按官方文档约束（ShardingSphere 5.5.x 集成 Seata）：

- **不允许**在业务方法上叠加 `@GlobalTransactional` —— ShardingSphere 自己充当 BM/RM/SRM（TM/RM），
  仍使用 Spring `@Transactional`（本项目本来就是这么写的，天然契合，业务代码零改动）；
- 每个**物理库**都要预建 `undo_log` 表（AT 回滚依据），由 sharding 装配代码在启动时自动创建；
- `seata.enable-auto-data-source-proxy=false`：明确不使用 Seata 自身的 DataSource 代理（避免与 SS 二次代理冲突）。

## 三、实现细节（代码地图）

| 文件 | 作用 |
|---|---|
| `alarm-server/src/main/java/com/alarm/storage/ShardingStorageConfig.java` | `@Profile("sharding")` 下用 **Spring 属性动态生成 ShardingSphere YAML**，装配 `@Primary DataSource`（`YamlShardingSphereDataSourceFactory`）。凭据/地址全部来自环境变量，**不进 git** |
| `alarm-server/src/main/java/com/alarm/storage/UuidHashShardAlgorithm.java` | 自定义 CLASS_BASED 标准分片算法：`ds = floorMod(uuid.hashCode(), 2)`。SS 内置 `HASH_MOD` 是 auto 模式专用（与显式 `actualDataNodes` 互斥，实测报错），自实现后路由确定、可在单测里独立断言 |
| `alarm-server/src/main/resources/application-sharding.yml` | 三个物理库 URL（默认指向 `mysql.middleware.svc.cluster.local:3306/alarm110_0|_1|_sys`）+ `username/password`（复用 `alarm-mysql-secret`）+ `ALARM_SEATA_ENABLED` 开关 |
| `alarm-server/src/main/resources/seata.conf` | SS↔Seata 集成配置：`client.application.id=alarm-server`、事务组 `alarm110-tx-group`、全局事务超时 60s |
| `alarm-server/src/main/resources/registry.conf` / `file.conf` | Seata Client 侧 file registry/config：vgroup `alarm110-tx-group → default`，直连 `seata-server.alarm110.svc.cluster.local:8091` |
| `alarm-server/src/main/resources/schema.sql` | 兼容化改造（SS 解析器限制）：`CLOB`→`VARCHAR(4096)`、去掉 `CREATE INDEX IF NOT EXISTS`（索引在生产由 schema-mysql.sql/建库脚本承担）；单测/k8s 单库语义不变 |
| `alarm-server/pom.xml` | 新增 `shardingsphere-jdbc`、`shardingsphere-transaction-base-seata-at`（均 5.5.2）与 `seata-all:2.2.0`（**必须排除其带出的 antlr4-runtime 4.8**，否则 SS 解析器 `ParseCancellationException` 类缺失，实测踩坑） |
| `alarm-server/src/test/java/com/alarm/AlarmShardingRoutingTest.java` | 本地 H2 三库路由回归（见 §6） |
| `deploy/k8s.yaml` | `seata-server` TC（file 存储）+ Service 8091；alarm-server 增加 sharding profile 环境变量 |

### 3.1 分片规则（等价 YAML，由装配代码生成）

```yaml
dataSources:
  ds_0: { jdbcUrl: .../alarm110_0, username: alarm110, ... }
  ds_1: { jdbcUrl: .../alarm110_1, ... }
  sys:  { jdbcUrl: .../alarm110_sys, ... }
rules:
  - !SHARDING
    tables:
      incident:
        actualDataNodes: ds_${0..1}.incident
    defaultDatabaseStrategy:
      standard:
        shardingColumn: id                 # 物理列名（= incidentId）
        shardingAlgorithmName: alarm-uuid-hash
    shardingAlgorithms:
      alarm-uuid-hash:
        type: CLASS_BASED
        props:
          strategy: STANDARD
          algorithmClassName: com.alarm.storage.UuidHashShardAlgorithm
          sharding-count: 2
  - !SINGLE
    defaultDataSource: sys                 # 其余全部单表落 sys
transaction:                               # seata-enabled=true 时注入，否则 LOCAL
  defaultType: BASE
  providerType: Seata
```

要点补记（全部为实测结论）：

- 分片列名是**物理列** `id`，不是领域名 `incidentId`（用错名字会在 INSERT 时报
  "sharding conditions ... routing to multiple data nodes"）；
- `!SINGLE.defaultDataSource=sys` 让所有非分片表（含运行期新建的）都落 sys，分片库绝不出现单表；
- `spring.sql.init` 的建表语句经 SS 逻辑 SQL 执行：分片表 DDL 广播到 ds_0/ds_1，单表 DDL 落 sys——**三库首次启动自动建出全部表**，无需手工执行 DDL。

## 四、Seata 部署与链路（k3s 实际拓扑）

```
alarm-server Pod (k8s+sharding profile)                     seata-server TC Pod
┌───────────────────────────────┐   8091 TCP(全局事务/RM)   ┌──────────────────┐
│ ShardingSphereDataSource      │ ───────────────────────► │ file 存储最小部署  │
│  ├ ds_0 → alarm110_0 (incident│ ◄── XID 传播/分支注册 ─── │ (单实例，无盘依赖) │
│  ├ ds_1 → alarm110_1 (incident│                          └──────────────────┘
│  └ sys  → alarm110_sys (undo_log 等)                      │
└───────────────────────────────┘
```

- TC：`deploy/k8s.yaml` 内 `seata-server` Deployment（`apache/seata-server:2.2.0`，file 存储，
  镜像走仓库 `docker.1panel.live` 会 403，须按集群惯例离线搬运，见 §7 注意事项），Service 仅集群内可达；
- 每个物理库的 `undo_log` 由应用启动时自动 `CREATE TABLE IF NOT EXISTS`（无条件，幂等）；
- 事务组：`alarm110-tx-group`，两端（seata.conf 与 file.conf）一致。

## 五、Seata 应用现状（截至 2026-11-01）

| 项 | 状态 |
|---|---|
| 代码/配置/测试 | ✅ 已合入（本地 40 测试全绿，含 3 条分片路由回归） |
| 镜像 | ✅ `alarm-server:git-c371e98` 系列迭代后的 `git-4938156`（本次运行镜像） |
| 集群分片库 | ✅ `alarm110_0/1/sys` 三库 + 应用首启自动建表（见下"集群实测"） |
| TC 部署 | ✅ `seata-server` Deployment（官方镜像 `apache/seata-server:2.2.0`，`STORE_MODE=file`，Service 8091 集群内） |
| 生产开关 | ✅ Deployment 已带 `SPRING_PROFILES_ACTIVE=k8s,sharding` + `ALARM_SEATA_ENABLED=true` |
| 集群端到端 | ✅ k3s 冒烟 27/27（分片+AT 形态，`docs/evidence/2026-11-01/k3s-smoke-shard-seata.json`） |

### 5.1 集群实测证据（⚠️ 关键，请勿省略复核）

- **分片分布 & 守恒**（mysql 直查三库）：

  ```
  alarm110_0.incident = 2   alarm110_1.incident = 1   （3 张警单哈希落两库）
  alarm110_sys.outbox_event: 20 条，20 条 PUBLISHED（无 PENDING 积压）
  alarm110_sys.idempotency_record = 17（幂等键/防重全在 sys 全局唯一）
  ```
- **Seata AT 全链**（TC 日志）：

  ```
  xid=10.42.3.91:8091:3702798924573778424 下 3 个分支（incident 分片库分支 + idempotency + outbox 的 sys 库分支）
  三个分支全部 PhaseTwo_Committed；undo_log 在事务提交后立即清理（两库残留均为 0）
  ```
- **事务一致性实战验证**：此前 TC 未就绪期间多次 CrashLoop 的各次写入，在三库中**零残留孤儿**——
  恰好演示了"任一分支未注册成功即失败、无半提交数据"的 AT 语义；
- 本地 `AlarmShardingRoutingTest` 3 用例：18 张警单双分片均匀落库、单表系统/schema 无越界、防重幂等语义与单库一致。

### 5.2 与旧形态的兼容

回滚按钮仍在：把 Deployment 的 `SPRING_PROFILES_ACTIVE` 改回 `k8s` 并设 `ALARM_SEATA_ENABLED=false`
即回到单库 `alarm110` 形态（数据仍在，`alarm110` 库表齐全）；分片库数据迁移回单库已按 `incident_id`
哈希可重放，工具脚本后续补充。

## 六、验收（测试矩阵）

### 6.1 本地（CI 级，40/40 绿）

- 默认 profile：29 个既有用例（12 领域 + 17 MockMvc）——**业务零回归**；
- `sharding` profile（新增 `AlarmShardingRoutingTest`）：
  1. 建 18 张警单 → 既能在逻辑层全量列出（跨片合并），又能**直查两物理 H2 库证明两分片都落了数据且总数守恒**；
  2. outbox/幂等只落 `sys`（直查分片库确认表不存在/为空），防重复建单仍返回同一警单**且不新增 outbox**；
  3. 单查经分片路由点查命中、审计经 sys 正常。

### 6.2 集群（k3s 实测）

- 冒烟脚本 `scripts/k3s-smoke.py`（27 用例）在 sharding+Seata 形态下复跑全部通过；
- 集群侧验证（mysql pod 直查）：
  ```
  alarm110_0: incident 行数 n0 ; alarm110_1: incident 行数 n1 ; n0+n1 = 逻辑总数
  alarm110_sys: outbox_published = 总事件数（Seata AT 提交，无孤儿/无残留）
  ```
- Seata 链路证据：TC 日志出现 RM/TM 注册与分支记录；`alarm110_sys.undo_log` 正常生成并在全局提交后清理。

## 七、运维手册

### 7.1 部署/回滚

```bash
# 部署（TC 与应用一起）
kubectl apply -f deploy/k8s.yaml
kubectl -n alarm110 rollout status deploy/alarm-server

# 回滚到单库形态（分钟级）：只改环境变量滚动重启即可，分片库数据仍在
#   SPRING_PROFILES_ACTIVE: k8s          （去掉 sharding）
#   ALARM_SEATA_ENABLED:    "false"
```

### 7.3 一次性建库脚本（生产形态首次部署 / 或从单库迁移时用）

```bash
# 1) 三库与账号授权（middleware mysql pod，root 凭据经 secret）
CREATE DATABASE alarm110_0/1/sys ...; GRANT ...;   # 已在 2026-11-01 执行

# 2) 二级索引（SS 启动脚本不做索引；MySQL 8 无 CREATE INDEX IF NOT EXISTS，按存在性判断）
idx_dispatch_incident ON alarm110_sys.dispatch_task(incident_id)
idx_outbox_state      ON alarm110_sys.outbox_event(state, created_at)
idx_audit_target      ON alarm110_sys.audit_record(target_type, target_id)
# 已于 2026-11-01 执行并验收；后续新增分片/单表照此办理

# 3) undo_log 由应用启动自动建（Seata AT 官方 schema），无需手工
```

### 7.4 验收与回归

```bash
python scripts/k3s-smoke.py --base http://<node>:30085 --out docs/evidence/k3s-smoke.json   # 27 用例基线
# 分片专项：本地 mvn -Dtest=AlarmShardingRoutingTest（H2 双库）；集群侧用 §5.1 三库直查 + TC 日志判 AT
```

1. 镜像拉取：`apache/seata-server` 是官方新名（旧 `apache/incubator-seata:v*` 与 `seataio/seata-server` 都已不可用），
   且 `docker.1panel.live` 对它 403——需 `docker pull` 后 `docker save` → 逐节点 `k3s ctr images import`；
2. SS YAML 的分片列用**物理列名 `id`**；auto 算法（HASH_MOD 等）禁止与显式 `actualDataNodes` 混用；
3. 每个 SS 物理库必须能承载 `undo_log`（MySQL 8 用 `ENGINE=InnoDB` 版 DDL）；
4. 逻辑层 `SELECT COUNT(*) FROM incident`（跨片合并）是近似融合分页语义，`limit/offset` 在两分片下正确性成立，
   深分页不推荐（列表接口已限制 limit ≤ 200）；
5. `kill` 应用 pod 后重启：SS 首启自动建表 + undo_log 幂等建表，均不产生多余错误（`continue-on-error` 只挡重复索引）。

## 八、踩坑实录（2026-11-01 实施记录）

| 现象 | 根因 | 处理 |
|---|---|---|
| `Algorithm 'HASH_MOD' initialization failed: default tables sharding configuration can not use auto sharding algorithm` | `HASH_MOD` 只服务 auto 分片，与 `defaultDatabaseStrategy.standard` 互斥 | 改为 CLASS_BASED 自定义标准算法 |
| `Unable to find property 'shardingColumn'` | 表级 auto 配置语法在 5.5.2 不存在 | 保持 rule 级 `defaultDatabaseStrategy.standard` |
| `NoClassDefFoundError: org/antlr/v4/runtime/misc/ParseCancellationException` | seata-all 带出的 antlr4 4.8 与 SS 5.5.2 需要的 4.10+ 冲突 | 仅在 `seata-all`/`transaction-base-seata-at` 排除 antlr；`shardingsphere-jdbc` 自带的 antlr 能力保留 |
| `no viable alternative at input 'IF'` / `payload CLOB` 解析失败 | SS SQL 解析器为 MySQL 方言，不认 `CREATE INDEX IF NOT EXISTS` 与 `CLOB` | schema.sql 改 VARCHAR(4096)、去掉 IF（生产索引迁到 schema-mysql.sql/建库脚本） |
| `Table or view 'outbox_event' does not exist` |DRV 单表落点：`!SINGLE.tables` 显式列表写法未生效 | 改用 `!SINGLE.defaultDataSource: sys`（所有非分片表统一落 sys，实测生效） |
| `routing to multiple data nodes` | 分片列名写成领域名 `incident_id`，物理列是 `id` | YAML 改 `shardingColumn: id` |
| `DuplicateIndexException: Index 'idx_dispatch_incident' already exists`（重启 CrashLoop） | `CREATE INDEX` 无 `IF NOT EXISTS`，SS 的 checker 直接抛错且 spring `continue-on-error` 拦不住 | 索引从 schema-mysql.sql 移出，一次性建库脚本负责（见 §7.3） |
| `NotSupportYetException: idempotency_record needs to contain the primary key` | **Seata AT 要求参与回滚的表必须有主键**，该表原本只有唯一约束 | 补自增 `id BIGINT AUTO_INCREMENT PRIMARY KEY`（H2/MySQL 两个 schema 同步，仓储 SQL 按列名写不受影响） |
| `Unknown column 'rollback_info' in 'field list'` | undo_log 必须用 **Seata 2.2.0 官方 schema**（`rollback_info/log_status/log_created/log_modified`），不能自造简化列 | 采用官方结构（见 ShardingStorageConfig 常量并 DROP 旧表重建） |
| TC 起不来：`Could not resolve placeholder 'logging.file.path'` | 自造 ConfigMap 覆盖了镜像内置 application.yml，破坏其必需配置 | 改用官方 env：`STORE_MODE=file`，不挂载 yml |
| TC 镜像 403 / `docker.io` 超时 | `docker.1panel.live` 拒绝镜像；镜像改名 `apache/seata-server:2.2.0` 后公网口径不佳 | win11 侧 Docker Desktop 配国内 registry-mirrors（`~/.docker/daemon.json`：docker.1ms.run/m.daocloud/hub.rat.dev）拉准后 `docker save` → 逐节点 `k3s ctr images import` |
| 新 Pod `ErrImagePull`/`CreateContainerError` 且集中在 `ubuntu2404-dev` | 外部 20 分钟前加入的新 worker 节点：缺镜像且 apparmor 装载失败（`apparmor_parser: Access denied`） | 两侧工作负载 Deployment 增加 `nodeAffinity NotIn[ubuntu2404-dev]`；节点修好后移除该 affinity |
| T02 起所有写接口 500：`can not connect to 10.43.x:8091` | `BASE/Seata` 模式下 **TC 可用性是强依赖**（TMClient 启动时必须连通），客户端 file.grouplist 指向的特征 | 集群内 svc 保活；回滚按钮见 §5.2；生产建议给 TC 做双实例（含 bib 存储升级）前不要大流量 |
| redis 预览接口 500：`Unable to connect to redis.middleware...:6379` | 集群 3h 前引入 default-deny `NetworkPolicy` 白名单，`allow-alarm110-to-middleware` 只放 mysql/kafka | 新增 `allow-alarm110-middleware-redis`（6379 增量放行，进 git 与现网一致）；同时为 middleware 供租户一致性的映射教训：新中间件接入要核对白名单端口 |

## 九、与设计一致性

- 本篇只是"存储拓扑升级"，不改业务语义：调查单/派遣状态机、幂等键（R02）、乐观锁、事件版本（R03）、
  预占守卫（R04）、终端自声明 TIMEOUT 拒绝（R05）在分片形态下的 k3s 冒烟中全部复验通过；
- `详细设计文档.md` 的表结构、API 出入参不变，仅提醒分库视图差异已在 §3 列清。
