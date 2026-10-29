package com.alarm.contracts.event.payload;

import com.alarm.contracts.enums.Channel;
import com.alarm.contracts.enums.Priority;

/** IncidentCreated 事件负载，不含敏感正文。 */
public record IncidentCreatedPayload(Channel channel, String sourceId, Priority priority, String type) {
}
