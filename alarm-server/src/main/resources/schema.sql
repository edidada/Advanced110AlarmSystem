-- 110 告警系统首版表结构（H2，可同构映射到生产关系库）
-- 依赖唯一约束提供业务幂等：incident(channel, source_id)、idempotency(scope, key)、outbox(event_id)

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
    location_observed_at TIMESTAMP,
    conclusion       VARCHAR(512),
    created_by       VARCHAR(64)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    closed_at        TIMESTAMP,
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
    deadline    TIMESTAMP   NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    CONSTRAINT uk_dispatch_command UNIQUE (command_id)
);
-- 索引统一在生产建库脚本/MySQL 方言（schema-mysql.sql、sharding 部署脚本）创建：
-- ShardingSphere 逻辑 SQL 解析器不支持 H2 风格 CREATE INDEX 元数据检查，且测试库量级无需索引。

CREATE TABLE IF NOT EXISTS unit_status (
    unit_id          VARCHAR(64) PRIMARY KEY,
    name             VARCHAR(64) NOT NULL,
    capabilities     VARCHAR(128),
    availability     INT         NOT NULL,
    location_x       DOUBLE,
    location_y       DOUBLE,
    location_source  VARCHAR(8),
    location_observed_at TIMESTAMP,
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
    payload           VARCHAR(4096) NOT NULL,
    trace_id          VARCHAR(64),
    state             VARCHAR(12) NOT NULL,
    created_at        TIMESTAMP   NOT NULL,
    published_at      TIMESTAMP
);

CREATE TABLE IF NOT EXISTS idempotency_record (
    scope           VARCHAR(96) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation       VARCHAR(64) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_body   VARCHAR(4096),
    response_status INT,
    state           VARCHAR(12) NOT NULL,
    created_at      TIMESTAMP   NOT NULL,
    CONSTRAINT uk_idempotency UNIQUE (scope, idempotency_key)
);

CREATE TABLE IF NOT EXISTS audit_record (
    id          VARCHAR(36) PRIMARY KEY,
    actor_id    VARCHAR(64) NOT NULL,
    action      VARCHAR(48) NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id   VARCHAR(64) NOT NULL,
    detail      VARCHAR(512),
    occurred_at TIMESTAMP   NOT NULL
);

