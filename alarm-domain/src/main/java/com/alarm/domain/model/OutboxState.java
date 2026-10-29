package com.alarm.domain.model;

/** Outbox 发布状态：PENDING 待发；PUBLISHED 已确认写入事件总线。 */
public enum OutboxState {
    PENDING,
    PUBLISHED
}
