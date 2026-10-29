package com.alarm.domain.exception;

import com.alarm.contracts.common.ApiError;
import com.alarm.contracts.common.ErrorCodes;

/** 资源不存在 -> HTTP 404 ALM-1004。 */
public class NotFoundException extends AlarmDomainException {
    public NotFoundException(String targetType, String targetId) {
        super(targetType + " not found: " + targetId,
                ApiError.of(ErrorCodes.NOT_FOUND, targetType + " not found: " + targetId));
    }
}
