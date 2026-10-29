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
}
