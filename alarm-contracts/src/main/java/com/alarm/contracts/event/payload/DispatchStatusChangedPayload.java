package com.alarm.contracts.event.payload;

import com.alarm.contracts.enums.DispatchStatus;

/** DispatchStatusChanged 事件负载。 */
public record DispatchStatusChangedPayload(String dispatchId, String unitId, DispatchStatus from, DispatchStatus to, String note) {
}
