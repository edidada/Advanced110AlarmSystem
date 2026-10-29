package com.alarm.contracts.api;

/** POST /api/v1/dispatches/{id}/cancel 请求体。 */
public record CancelDispatchRequest(long expectedVersion, String reason) {
}
