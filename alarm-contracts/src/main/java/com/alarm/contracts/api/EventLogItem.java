package com.alarm.contracts.api;

/** GET /api/v1/ops/events/{aggregateId} 单条事件流水（运维核对版本一致性用）。 */
public record EventLogItem(
        String eventType,
        long aggregateVersion,
        String incidentId,
        String occurredAt,
        String state) {
}
