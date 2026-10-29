package com.alarm.contracts.api;

import com.alarm.contracts.enums.DispatchStatus;

/** 警单详情内的派遣概要。 */
public record DispatchSummary(String dispatchId, String unitId, DispatchStatus status, long version) {
}
