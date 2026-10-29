package com.alarm.application.support;

import com.alarm.application.port.Clock;
import com.alarm.application.port.IdGenerator;
import com.alarm.application.port.OutboxRepository;
import com.alarm.contracts.event.EventEnvelope;
import com.alarm.domain.model.OutboxEvent;
import com.alarm.domain.model.OutboxState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 事件记录器：构建事件信封并序列化为 JSON 写入 Outbox，
 * 与业务写入同一事务（调用方负责 @Transactional）。
 */
@Component
public class EventRecorder {

    private final OutboxRepository outboxRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public EventRecorder(OutboxRepository outboxRepository, IdGenerator idGenerator,
                         Clock clock, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public void record(String eventType, String aggregateType, String aggregateId, long aggregateVersion,
                       String incidentId, String traceId, Object payload) {
        Instant now = clock.now();
        EventEnvelope envelope = new EventEnvelope(
                idGenerator.nextId(), eventType, 1, now.toString(),
                aggregateType, aggregateId, aggregateVersion, incidentId, traceId, payload);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            outboxRepository.insert(new OutboxEvent(envelope.eventId(), eventType, incidentId,
                    aggregateType, aggregateId, aggregateVersion, json, traceId,
                    OutboxState.PENDING, now, null));
        } catch (Exception e) {
            throw new IllegalStateException("serialize event envelope failed: " + eventType, e);
        }
    }
}
