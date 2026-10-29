package com.alarm.contracts.api;

import com.alarm.contracts.enums.DispatchStatus;

import java.time.Instant;

/** 单条派遣任务创建结果。 */
public record CreatedDispatchItem(
        String dispatchId,
        String unitId,
        String commandId,
        DispatchStatus status,
        Instant deadline,
        long version) {
}
