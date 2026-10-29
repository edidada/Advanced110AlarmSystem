package com.alarm.contracts.api;

import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.Channel;
import com.alarm.contracts.enums.IncidentStatus;
import com.alarm.contracts.enums.Priority;

import java.time.Instant;
import java.util.List;

/** GET /api/v1/incidents/{id} 响应体。 */
public record IncidentResponse(
        String incidentId,
        Channel channel,
        String sourceId,
        IncidentStatus status,
        Priority priority,
        String type,
        String description,
        String verifiedAddress,
        LocationDto location,
        String conclusion,
        String createdBy,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        List<DispatchSummary> dispatches) {
}
