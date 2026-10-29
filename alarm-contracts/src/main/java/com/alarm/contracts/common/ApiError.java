package com.alarm.contracts.common;

import java.util.List;

/** 统一错误正文，仅包含可公开的安全信息，不含警情敏感正文。 */
public record ApiError(String code, String message, List<String> details) {
    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }

    public static ApiError of(String code, String message, List<String> details) {
        return new ApiError(code, message, details);
    }
}
