package com.alarm.web;

import com.alarm.contracts.common.ApiError;
import org.springframework.http.HttpStatus;

/** 必填请求头缺失（区分未认证 401 与缺少幂等键 400）。 */
public class MissingHeaderException extends RuntimeException {
    private final HttpStatus status;
    private final ApiError error;

    public MissingHeaderException(String message, HttpStatus status, ApiError error) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public HttpStatus status() {
        return status;
    }

    public ApiError error() {
        return error;
    }
}
