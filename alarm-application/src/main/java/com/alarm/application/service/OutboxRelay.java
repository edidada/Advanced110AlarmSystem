package com.alarm.application.service;

import com.alarm.application.port.Clock;
import com.alarm.application.port.EventPublisher;
import com.alarm.application.port.OutboxRepository;
import com.alarm.domain.model.OutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 补发器：扫描 PENDING 事件逐条 publish 并标记 PUBLISHED（至少一次投递语义）。
 * 总线不可用时保留事件，由定时器按批次限速补发；崩溃恢复重复发布由消费端去重。
 */
@Service
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final EventPublisher publisher;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelay(OutboxRepository outboxRepository,
                       EventPublisher publisher,
                       Clock clock,
                       @Value("${alarm.outbox-batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** 执行一轮补发，返回成功发布数量。发布失败不标记、留在 PENDING。 */
    public int runOnce() {
        List<OutboxEvent> pending = outboxRepository.findPendingBatch(batchSize);
        if (pending.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (OutboxEvent event : pending) {
            try {
                publisher.publish(event);
            } catch (RuntimeException e) {
                continue; // 总线故障：保留 PENDING，等待下轮
            }
            Instant now = clock.now();
            outboxRepository.markPublished(event.getEventId(), now);
            sent++;
        }
        return sent;
    }

    /** 观测指标：最老未发布事件年龄（秒）。 */
    public long oldestPendingAgeSeconds() {
        List<OutboxEvent> batch = outboxRepository.findPendingBatch(1);
        if (batch.isEmpty()) {
            return 0;
        }
        Instant createdAt = batch.get(0).getCreatedAt();
        if (createdAt == null) {
            return 0;
        }
        return Math.max(0, java.time.Duration.between(createdAt, clock.now()).getSeconds());
    }
}
