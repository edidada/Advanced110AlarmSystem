package com.alarm.contracts.event.payload;

/** IncidentClosed 事件负载。 */
public record IncidentClosedPayload(String conclusion, String closedBy, String closedAt) {
}
