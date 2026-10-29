package com.alarm.contracts.api;

import com.alarm.contracts.enums.IncidentStatus;

/** POST /api/v1/incidents/{id}/close 请求体。 */
public record CloseIncidentRequest(long expectedVersion, String conclusion) {
}
