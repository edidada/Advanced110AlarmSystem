package com.alarm.contracts.api;

import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.Channel;
import com.alarm.contracts.enums.Priority;

/** POST /api/v1/incidents 请求体。 */
public record CreateIncidentRequest(
        Channel channel,
        String sourceId,
        Priority priority,
        String type,
        String description,
        LocationDto location) {
}
