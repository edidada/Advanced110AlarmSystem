package com.alarm.domain.exception;

import com.alarm.contracts.common.ApiError;
import com.alarm.contracts.common.ErrorCodes;

/** 参数格式/枚举非法 -> HTTP 400 ALM-0901。 */
public class IllegalRequestException extends AlarmDomainException {
    public IllegalRequestException(String message) {
        super(message, ApiError.of(ErrorCodes.INVALID_ARGUMENT, message));
    }
}
