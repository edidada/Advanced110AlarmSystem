package com.alarm.contracts.api;

import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.IncidentStatus;

/** PATCH /api/v1/incidents/{id} 响应体。 */
public record IncidentUpdatedResponse(String incidentId, IncidentStatus status, long version) {
}
