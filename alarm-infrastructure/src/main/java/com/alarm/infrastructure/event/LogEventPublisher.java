package com.alarm.infrastructure.event;

import com.alarm.application.port.EventPublisher;
import com.alarm.domain.model.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 事件总线适配器（当前版本：结构化日志模拟，预留 Kafka 适配位）。
 * 生产替换点：以 KafkaTemplate 实现 EventPublisher，保持 payload 即信封 JSON 不变。
 */
@Component
public class LogEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger("alarm.event-bus");

    @Override
    public void publish(OutboxEvent event) {
        log.info("[outbox->bus] eventType={} eventId={} incidentId={} aggregate={}/{} v={} traceId={} payload={}",
                event.getEventType(), event.getEventId(), event.getIncidentId(),
                event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(),
                event.getTraceId(), event.getPayload());
    }
}
