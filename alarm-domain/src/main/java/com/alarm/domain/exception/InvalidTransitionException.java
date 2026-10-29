package com.alarm.domain.exception;

import com.alarm.contracts.common.ApiError;
import com.alarm.contracts.common.ErrorCodes;
import com.alarm.contracts.enums.DispatchStatus;
import com.alarm.contracts.enums.IncidentStatus;

/** 状态机不允许的迁移或业务态冲突（含警力可用性竞争）-> HTTP 409 ALM-1003。 */
public class InvalidTransitionException extends AlarmDomainException {
    public InvalidTransitionException(IncidentStatus from, IncidentStatus to) {
        super("incident transition denied: " + from + " -> " + to,
                ApiError.of(ErrorCodes.STATE_CONFLICT, "incident transition denied: " + from + " -> " + to));
    }

    public InvalidTransitionException(DispatchStatus from, DispatchStatus to) {
        super("dispatch transition denied: " + from + " -> " + to,
                ApiError.of(ErrorCodes.STATE_CONFLICT, "dispatch transition denied: " + from + " -> " + to));
    }

    /** 带业务原因码的构造：reasonCode 进入 details[0]，便于客户端区分（如 UNIT_UNAVAILABLE）。 */
    public InvalidTransitionException(String reasonCode, String detail) {
        super(detail, ApiError.of(ErrorCodes.STATE_CONFLICT, detail, java.util.List.of(reasonCode)));
    }

    public InvalidTransitionException(String reasonCode, IncidentStatus from, IncidentStatus to) {
        super("incident transition denied: " + from + " -> " + to,
                ApiError.of(ErrorCodes.STATE_CONFLICT, "incident transition denied: " + from + " -> " + to,
                        java.util.List.of(reasonCode)));
    }

    public InvalidTransitionException(String reasonCode, DispatchStatus from, DispatchStatus to) {
        super("dispatch transition denied: " + from + " -> " + to,
                ApiError.of(ErrorCodes.STATE_CONFLICT, "dispatch transition denied: " + from + " -> " + to,
                        java.util.List.of(reasonCode)));
    }
}
