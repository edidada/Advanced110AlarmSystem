package com.alarm.contracts.api;

import java.time.Instant;

/** GET /api/v1/audits/{targetType}/{targetId} 单条审计记录。 */
public record AuditEntryResponse(
        String id,
        String actorId,
        String action,
        String targetType,
        String targetId,
        String detail,
        Instant occurredAt) {
}
