package com.alarm.infrastructure.event;

import com.alarm.application.port.EventPublisher;
import com.alarm.domain.model.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 事件总线适配器（Kafka 版）：对接集群 middleware 命名空间的 Kafka（alarm.bus.events 主题）。
 * - key = incidentId（无全局警单号时用 aggregateId），同警单事件保序进同一分区；
 * - payload 即 Outbox 信封 JSON，与日志版完全一致，消费端无感切换；
 * - 同步等待发送确认（10s），失败抛出由 OutboxRelay 保留 PENDING，下一轮补发（至少一次语义不变）。
 */
@Component
@ConditionalOnProperty(prefix = "alarm.event-bus", name = "type", havingValue = "kafka")
public class KafkaEventPublisher implements EventPublisher {

    public static final String TOPIC = "alarm.bus.events";
    public static final int SEND_TIMEOUT_SECONDS = 10;

    private static final Logger log = LoggerFactory.getLogger("alarm.event-bus");

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(TOPIC, keyOf(event), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("kafka publish interrupted, eventId=" + event.getEventId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("kafka publish failed, eventId=" + event.getEventId()
                    + ", cause=" + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()), e);
        }
        log.info("[outbox->kafka] topic={} eventType={} eventId={} incidentId={} aggregate={}/{} v={} traceId={}",
                TOPIC, event.getEventType(), event.getEventId(), event.getIncidentId(),
                event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(), event.getTraceId());
    }

    private static String keyOf(OutboxEvent event) {
        return event.getIncidentId() != null ? event.getIncidentId() : event.getAggregateId();
    }
}
