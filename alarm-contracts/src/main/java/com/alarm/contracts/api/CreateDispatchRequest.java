package com.alarm.contracts.api;

import java.util.List;

/** POST /api/v1/incidents/{id}/dispatches 请求体。 */
public record CreateDispatchRequest(
        long expectedVersion,
        List<String> unitIds,
        String note,
        Integer deadlineSeconds) {
}
