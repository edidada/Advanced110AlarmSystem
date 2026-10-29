package com.alarm.contracts.event.payload;

import com.alarm.contracts.enums.IncidentStatus;
import com.alarm.contracts.enums.VerifyAction;

import java.util.List;

/** IncidentUpdated 事件负载。 */
public record IncidentUpdatedPayload(
        VerifyAction action,
        IncidentStatus status,
        List<String> changedFields,
        String reason) {
    public IncidentUpdatedPayload {
        changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    }
}
