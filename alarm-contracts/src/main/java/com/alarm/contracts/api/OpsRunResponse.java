package com.alarm.contracts.api;

/** 运维触发类接口（Outbox 补发/派遣超时扫描）响应体。 */
public record OpsRunResponse(int processed, String ranAt) {
}
