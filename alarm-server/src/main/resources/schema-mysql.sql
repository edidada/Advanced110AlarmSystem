-- 110 告警系统表结构（MySQL 8 方言，部署于 k3s 时配合 middleware 命名空间的 mysql 服务使用）
-- 与 schema.sql（H2）同构；差异点：
--   * CLOB -> MEDIUMTEXT
--   * TIMESTAMP -> DATETIME(3)：MySQL 的 TIMESTAMP 带隐式 DEFAULT CURRENT_TIMESTAMP/
--     ON UPDATE CURRENT_TIMESTAMP，会篡改 created_at 语义，DATETIME 无隐式行为
--   * MySQL 8 不支持 CREATE INDEX IF NOT EXISTS：重复初始化由
--     spring.sql.init.continue-on-error=true 按“Duplicate key name”跳过
-- 依赖唯一约束提供业务幂等：incident(channel, source_id)、idempotency(scope, idempotency_key)、outbox(event_id)

CREATE TABLE IF NOT EXISTS incident (
    id               VARCHAR(36) PRIMARY KEY,
    channel          VARCHAR(16)  NOT NULL,
    source_id        VARCHAR(64)  NOT NULL,
    status           VARCHAR(24)  NOT NULL,
    priority         VARCHAR(8)   NOT NULL,
    type             VARCHAR(32)  NOT NULL,
    description      VARCHAR(512),
    verified_address VARCHAR(256),
    location_x       DOUBLE,
    location_y       DOUBLE,
    location_source  VARCHAR(8),
    location_observed_at DATETIME(3),
    conclusion       VARCHAR(512),
    created_by       VARCHAR(64)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       DATETIME(3)  NOT NULL,
    updated_at       DATETIME(3)  NOT NULL,
    closed_at        DATETIME(3),
    CONSTRAINT uk_incident_source UNIQUE (channel, source_id)
);

CREATE TABLE IF NOT EXISTS dispatch_task (
    id          VARCHAR(36) PRIMARY KEY,
    incident_id VARCHAR(36) NOT NULL,
    unit_id     VARCHAR(64) NOT NULL,
    command_id  VARCHAR(64) NOT NULL,
    status      VARCHAR(24) NOT NULL,
    note        VARCHAR(256),
    assigned_by VARCHAR(64) NOT NULL,
    deadline    DATETIME(3) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  DATETIME(3) NOT NULL,
    updated_at  DATETIME(3) NOT NULL,
    CONSTRAINT uk_dispatch_command UNIQUE (command_id)
);
-- 索引由一次性建库脚本创建（本 SQL 每次启动经 SS 解析重复执行，CREATE INDEX 无 IF NOT EXISTS 会中止初始化）

CREATE TABLE IF NOT EXISTS unit_status (
    unit_id          VARCHAR(64) PRIMARY KEY,
    name             VARCHAR(64) NOT NULL,
    capabilities     VARCHAR(128),
    availability     INT         NOT NULL,
    location_x       DOUBLE,
    location_y       DOUBLE,
    location_source  VARCHAR(8),
    location_observed_at DATETIME(3),
    last_dispatch_id VARCHAR(36),
    version          BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS outbox_event (
    event_id          VARCHAR(36) PRIMARY KEY,
    event_type        VARCHAR(48) NOT NULL,
    incident_id       VARCHAR(36),
    aggregate_type    VARCHAR(24) NOT NULL,
    aggregate_id      VARCHAR(36) NOT NULL,
    aggregate_version BIGINT      NOT NULL,
    payload           MEDIUMTEXT  NOT NULL,
    trace_id          VARCHAR(64),
    state             VARCHAR(12) NOT NULL,
    created_at        DATETIME(3) NOT NULL,
    published_at      DATETIME(3)
);

CREATE TABLE IF NOT EXISTS idempotency_record (
    scope           VARCHAR(96) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation       VARCHAR(64) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_body   MEDIUMTEXT,
    response_status INT,
    state           VARCHAR(12) NOT NULL,
    created_at      DATETIME(3) NOT NULL,
    CONSTRAINT uk_idempotency UNIQUE (scope, idempotency_key)
);

CREATE TABLE IF NOT EXISTS audit_record (
    id          VARCHAR(36) PRIMARY KEY,
    actor_id    VARCHAR(64) NOT NULL,
    action      VARCHAR(48) NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id   VARCHAR(64) NOT NULL,
    detail      VARCHAR(512),
    occurred_at DATETIME(3) NOT NULL
);
