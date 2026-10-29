package com.alarm.application.support;

/** 操作人上下文：模拟认证当前仅携带操作员标识与追踪标识。 */
public record OperatorContext(String operatorId, String traceId) {
}
