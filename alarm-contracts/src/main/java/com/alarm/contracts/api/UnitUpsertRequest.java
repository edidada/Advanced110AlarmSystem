package com.alarm.contracts.api;

import com.alarm.contracts.common.LocationDto;

import java.util.List;

/** POST /api/v1/units 请求体（upsert，按 unitId 幂等）。 */
public record UnitUpsertRequest(
        String unitId,
        String name,
        List<String> capabilities,
        Integer availability,
        LocationDto location) {
}
