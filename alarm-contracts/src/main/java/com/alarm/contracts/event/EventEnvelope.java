package com.alarm.contracts.event;

/**
 * 事件信封。Outbox 行的 payload 即为完整信封 JSON，
 * 消费端以 consumerName + eventId 去重，以 aggregateVersion 检测重复/旧版本/缺口。
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        int schemaVersion,
        String occurredAt,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String incidentId,
        String traceId,
        Object payload) {
}
