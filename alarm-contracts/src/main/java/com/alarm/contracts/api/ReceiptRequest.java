package com.alarm.contracts.api;

import com.alarm.contracts.enums.DispatchStatus;

import java.time.Instant;

/** POST /api/v1/dispatches/{id}/receipts 请求体：终端按 commandId 幂等上报。 */
public record ReceiptRequest(
        String commandId,
        DispatchStatus targetStatus,
        long expectedVersion,
        String note,
        Instant occurredAt) {
}
