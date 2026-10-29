package com.alarm.contracts.api;

import java.util.List;

/** POST /api/v1/incidents/{id}/dispatches 响应体；warnings 提示非阻断问题（如警力位置过期）。 */
public record CreateDispatchResponse(long incidentVersion, List<CreatedDispatchItem> dispatches, List<String> warnings) {
    public CreateDispatchResponse {
        dispatches = dispatches == null ? List.of() : List.copyOf(dispatches);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
