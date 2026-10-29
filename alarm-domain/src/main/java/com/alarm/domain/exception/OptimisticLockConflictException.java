package com.alarm.domain.exception;

import com.alarm.contracts.common.ApiError;
import com.alarm.contracts.enums.IncidentStatus;

/** 乐观锁版本冲突（条件更新影响行数为 0）-> HTTP 409 ALM-1002。 */
public class OptimisticLockConflictException extends AlarmDomainException {

    public OptimisticLockConflictException(IncidentStatus aggregate) {
        this("version conflict on " + aggregate);
    }

    public OptimisticLockConflictException(long expectedVersion) {
        this("version conflict, expected version " + expectedVersion);
    }

    public OptimisticLockConflictException(String message) {
        super(message, ApiError.of(com.alarm.contracts.common.ErrorCodes.VERSION_CONFLICT, message));
    }
}
