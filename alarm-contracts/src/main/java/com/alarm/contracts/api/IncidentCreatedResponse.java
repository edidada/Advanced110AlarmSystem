package com.alarm.contracts.api;

import com.alarm.contracts.enums.IncidentStatus;

/** POST /api/v1/incidents 响应体。 */
public record IncidentCreatedResponse(String incidentId, IncidentStatus status, long version) {
}
