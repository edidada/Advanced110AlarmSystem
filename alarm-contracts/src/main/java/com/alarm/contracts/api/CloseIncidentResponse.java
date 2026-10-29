package com.alarm.contracts.api;

import com.alarm.contracts.enums.IncidentStatus;

/** POST /api/v1/incidents/{id}/close 响应体。 */
public record CloseIncidentResponse(String incidentId, IncidentStatus status, long version) {
}
