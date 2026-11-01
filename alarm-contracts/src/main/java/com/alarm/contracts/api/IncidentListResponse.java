package com.alarm.contracts.api;

import java.util.List;

/** GET /api/v1/incidents 响应体：分页警单列表。 */
public record IncidentListResponse(
        List<IncidentSummary> items,
        long total,
        int limit,
        int offset) {
}
