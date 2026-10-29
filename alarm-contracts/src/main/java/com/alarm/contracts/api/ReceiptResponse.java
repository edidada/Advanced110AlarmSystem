package com.alarm.contracts.api;

import com.alarm.contracts.enums.DispatchStatus;

/** POST /api/v1/dispatches/{id}/receipts 响应体。 */
public record ReceiptResponse(String dispatchId, DispatchStatus status, long version) {
}
