package com.alarm.domain.exception;

import com.alarm.contracts.common.ApiError;
import com.alarm.contracts.common.ErrorCodes;

/** 幂等键冲突（同 key 不同请求体，或同 key 请求进行中）-> HTTP 409 ALM-2102。 */
public class IdempotencyConflictException extends AlarmDomainException {
    public IdempotencyConflictException(String message) {
        super(message, ApiError.of(ErrorCodes.IDEMPOTENCY_CONFLICT, message));
    }
}
