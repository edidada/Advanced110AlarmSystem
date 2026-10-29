package com.alarm.contracts.api;

/** POST /api/v1/units 响应体。 */
public record UnitUpsertResponse(String unitId, long version) {
}
