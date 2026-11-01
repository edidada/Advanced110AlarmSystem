package com.alarm.infrastructure.event;

import com.alarm.application.port.EventPublisher;
import com.alarm.domain.model.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 事件总线适配器（日志版）：结构化日志模拟，无外部依赖，供本地/单测使用。
 * Kafka 版适配器见 {@link KafkaEventPublisher}，通过 alarm.event-bus.type 切换。
 */
@Component
@ConditionalOnProperty(prefix = "alarm.event-bus", name = "type", havingValue = "log", matchIfMissing = true)
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
