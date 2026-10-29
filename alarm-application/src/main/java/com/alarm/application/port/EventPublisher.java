package com.alarm.application.port;

import com.alarm.domain.model.OutboxEvent;

/**
 * 事件发布端口。基础设施实现负责写入消息总线（当前为日志模拟，预留 Kafka 适配）。
 * 每次 publish 至少一次投递，调用成功后由调用方标记 PUBLISHED。
 */
public interface EventPublisher {
    void publish(OutboxEvent event);
}
