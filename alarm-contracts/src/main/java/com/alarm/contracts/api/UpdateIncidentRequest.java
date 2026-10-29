package com.alarm.contracts.api;

import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.Priority;
import com.alarm.contracts.enums.VerifyAction;

/** PATCH /api/v1/incidents/{id} 请求体：expectedVersion 必填；action 必填；依据动作补充 reason 等字段。 */
public record UpdateIncidentRequest(
        long expectedVersion,
        VerifyAction action,
        Priority priority,
        String type,
        String description,
        String verifiedAddress,
        LocationDto location,
        String reason) {
}
