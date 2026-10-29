package com.alarm.application.port;

import com.alarm.domain.model.OutboxEvent;
import com.alarm.domain.model.OutboxState;

import java.time.Instant;
import java.util.List;

/** Outbox 仓储端口（事件与业务同事务插入）。 */
public interface OutboxRepository {
    void insert(OutboxEvent event);

    List<OutboxEvent> findPendingBatch(int limit);

    void markPublished(String eventId, Instant publishedAt);

    long countByState(OutboxState state);

    /** 按聚合 id（如派送任务 id）查询事件流水，供运维核对版本一致性。 */
    List<OutboxEvent> listByAggregateId(String aggregateId);
}
