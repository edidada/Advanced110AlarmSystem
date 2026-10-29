package com.alarm.web;

import com.alarm.application.support.OperatorContext;
import com.alarm.contracts.common.ApiError;
import com.alarm.contracts.common.ErrorCodes;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/** 请求头解析与操作人上下文构造（模拟认证）。 */
final class WebSupport {

    static final String HEADER_OPERATOR = "X-Operator-Id";
    static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String HEADER_TRACE = "X-Trace-Id";

    private WebSupport() {
    }

    static OperatorContext operator(String operatorId, String traceIdHeader) {
        if (operatorId == null || operatorId.isBlank()) {
            throw new MissingHeaderException("X-Operator-Id header is required", HttpStatus.UNAUTHORIZED,
                    ApiError.of(ErrorCodes.UNAUTHENTICATED, "X-Operator-Id header is required"));
        }
        String traceId = traceIdHeader == null || traceIdHeader.isBlank()
                ? UUID.randomUUID().toString() : traceIdHeader;
        return new OperatorContext(operatorId.trim(), traceId);
    }

    static String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new MissingHeaderException("Idempotency-Key header is required", HttpStatus.BAD_REQUEST,
                    ApiError.of(ErrorCodes.MISSING_IDEMPOTENCY_KEY, "Idempotency-Key header is required"));
        }
        return key.trim();
    }
}
