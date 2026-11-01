package com.alarm.contracts.api;

import com.alarm.contracts.enums.Channel;
import com.alarm.contracts.enums.IncidentStatus;
import com.alarm.contracts.enums.Priority;

import java.time.Instant;

/** 警单列表行（GET /api/v1/incidents）：列表场景的精简投影。 */
public record IncidentSummary(
        String incidentId,
        Channel channel,
        String sourceId,
        IncidentStatus status,
        Priority priority,
        String type,
        String description,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
