package com.alarm.application.port;

import com.alarm.domain.model.IdempotencyRecord;
import com.alarm.domain.model.IdempotencyState;

import java.time.Instant;
import java.util.Optional;

/**
 * 幂等记录仓储端口。tryInsert 与业务写同事务；并发唯一键冲突不入异常,
 * 返回 false 让执行器改为读取既有记录。
 */
public interface IdempotencyRepository {
    Optional<IdempotencyRecord> find(String scope, String key);

    boolean tryInsertReserved(IdempotencyRecord record);

    void markCompleted(String scope, String key, int responseStatus, String responseBody, Instant at);

    /** 给 OutboxRelay 等运维接口使用。 */
    long countByState(IdempotencyState state);
}
