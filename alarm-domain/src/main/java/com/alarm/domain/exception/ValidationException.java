package com.alarm.domain.exception;

import com.alarm.contracts.common.ApiError;
import com.alarm.contracts.common.ErrorCodes;

/** 业务校验失败 -> HTTP 422 ALM-1005。 */
public class ValidationException extends AlarmDomainException {
    public ValidationException(String message) {
        super(message, ApiError.of(ErrorCodes.BUSINESS_VALIDATION, message));
    }

    public ValidationException(String message, java.util.List<String> details) {
        super(message, ApiError.of(ErrorCodes.BUSINESS_VALIDATION, message, details));
    }
}
