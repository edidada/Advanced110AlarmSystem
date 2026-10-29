package com.alarm.domain.exception;

import com.alarm.contracts.common.ApiError;
import com.alarm.contracts.common.ErrorCodes;

/** 领域异常基类，携带映射到 HTTP 的 ApiError。 */
public abstract class AlarmDomainException extends RuntimeException {
    private final ApiError apiError;

    protected AlarmDomainException(String message, ApiError apiError) {
        super(message);
        this.apiError = apiError;
    }

    public ApiError getApiError() {
        return apiError;
    }
}
