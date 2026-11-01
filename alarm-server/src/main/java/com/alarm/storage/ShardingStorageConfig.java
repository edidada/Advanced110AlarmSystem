package com.alarm.storage;

import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 分库分表数据源（Spring profile = sharding 时启用，作为 @Primary DataSource；
 * 不启用时一切沿用默认行为，单测与 k8s 单库部署不受任何影响）。
 *
 * 物理拓扑（middleware 命名空间 mysql，同一实例三个 schema，可平滑平移到独立实例）：
 *   ds_0 = alarm110_0 : 警单 incident 分片 0
 *   ds_1 = alarm110_1 : 警单 incident 分片 1
 *   sys  = alarm110_sys : 幂等表/Outbox/审计/警力/派遣等全局单表（唯一约束需全局可见的表不能跟随分片）
 *
 * 路由规则：
 *   incident 按 incident_id HASH_MOD 2 分片（主键派生，写入与点查全键路由、无二次抖动）；
 *   其余表由 SINGLE 规则 defaultDataSource=sys 承载。
 *
 * Seata AT（可选开关 alarm.storage.sharding.seata-enabled）：
 *   依赖 org.apache.shardingsphere:shardingsphere-transaction-base-seata-at + org.apache.seata:seata-all，
 *   ShardingSphere 作为全局事务的 TM/RM 参与方（业务代码仍用 Spring @Transactional，符合官方
 *   集成约束：不要在同一数据源上再叠加 @GlobalTransactional）。启用时每个物理库预建 undo_log
 *   表（AT 二阶段回滚依据）。
 */
@Configuration
@Profile("sharding")
public class ShardingStorageConfig {

    static final String UNDO_LOG_DDL_MYSQL = """
            CREATE TABLE IF NOT EXISTS undo_log (
                branch_id     BIGINT        NOT NULL,
                xid           VARCHAR(128)  NOT NULL,
                context       VARCHAR(128)  NOT NULL,
                rollback_info LONGBLOB      NOT NULL,
                log_status    INT           NOT NULL,
                log_created   DATETIME(6)   NOT NULL,
                log_modified  DATETIME(6)   NOT NULL,
                CONSTRAINT unq_undo_log_key UNIQUE (xid, branch_id)
            ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4""";

    static final String UNDO_LOG_DDL_H2 = """
            CREATE TABLE IF NOT EXISTS undo_log (
                branch_id     BIGINT        NOT NULL,
                xid           VARCHAR(128)  NOT NULL,
                context       VARCHAR(128)  NOT NULL,
                rollback_info VARBINARY(1048576) NOT NULL,
                log_status    INT           NOT NULL,
                log_created   TIMESTAMP(6)  NOT NULL,
                log_modified  TIMESTAMP(6)  NOT NULL,
                CONSTRAINT unq_undo_log_key UNIQUE (xid, branch_id)
            )""";

    @Bean
    @Primary
    public DataSource shardingDataSource(
            @Value("${alarm.storage.sharding.ds-0-url}") String ds0Url,
            @Value("${alarm.storage.sharding.ds-1-url}") String ds1Url,
            @Value("${alarm.storage.sharding.sys-url}") String sysUrl,
            @Value("${alarm.storage.sharding.username}") String username,
            @Value("${alarm.storage.sharding.password}") String password,
            @Value("${alarm.storage.sharding.seata-enabled:false}") boolean seataEnabled,
            @Value("${alarm.storage.sharding.maximum-pool-size:4}") int poolSize) throws SQLException {
        if (seataEnabled) {
            createUndoLog(ds0Url, username, password);
            createUndoLog(ds1Url, username, password);
            createUndoLog(sysUrl, username, password);
        }
        String yaml = buildYaml(ds0Url, ds1Url, sysUrl, username, password, seataEnabled, poolSize);
        try {
            return YamlShardingSphereDataSourceFactory.createDataSource(yaml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("sharding datasource init failed", e);
        }
    }

    /** 按 ShardingSphere 5.5.2 YAML 配置生成（事务段参考官方 Seata 集成文档）。 */
    static String buildYaml(String ds0Url, String ds1Url, String sysUrl,
                            String username, String password, boolean seataEnabled, int poolSize) {
        String driver = ds0Url.startsWith("jdbc:h2:") ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver";
        String transactionBlock = seataEnabled
                ? "transaction:\n  defaultType: BASE\n  providerType: Seata\n"
                : "transaction:\n  defaultType: LOCAL\n";
        return """
                dataSources:
                  ds_0:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: %s
                    jdbcUrl: %s
                    username: %s
                    password: %s
                    maximumPoolSize: %d
                  ds_1:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: %s
                    jdbcUrl: %s
                    username: %s
                    password: %s
                    maximumPoolSize: %d
                  sys:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: %s
                    jdbcUrl: %s
                    username: %s
                    password: %s
                    maximumPoolSize: %d
                rules:
                  - !SHARDING
                    tables:
                      incident:
                        actualDataNodes: ds_${0..1}.incident
                    defaultDatabaseStrategy:
                      standard:
                        shardingColumn: id
                        shardingAlgorithmName: alarm-uuid-hash
                    shardingAlgorithms:
                      alarm-uuid-hash:
                        type: CLASS_BASED
                        props:
                          strategy: STANDARD
                          algorithmClassName: com.alarm.storage.UuidHashShardAlgorithm
                          sharding-count: 2
                  - !SINGLE
                    defaultDataSource: sys
                %s
                props:
                  sql-show: false
                """.formatted(driver, ds0Url, username, password, poolSize,
                driver, ds1Url, username, password, poolSize,
                driver, sysUrl, username, password, poolSize,
                transactionBlock);
    }

    /** 在指定物理库预建 undo_log 表（AT 二阶段回滚依据；已存在则跳过）。 */
    static void createUndoLog(String url, String username, String password) {
        String ddl = url.startsWith("jdbc:h2:") ? UNDO_LOG_DDL_H2 : UNDO_LOG_DDL_MYSQL;
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("undo_log prepare failed for " + mask(url), e);
        }
    }

    static String mask(String url) {
        return url.replaceAll("(password=)[^&]*(&|$)", "$1***$2");
    }
}
