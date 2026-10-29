package com.alarm.web;

import com.alarm.application.port.OutboxRepository;
import com.alarm.application.service.OutboxRelay;
import com.alarm.application.support.OperatorContext;
import com.alarm.contracts.api.EventLogItem;
import com.alarm.contracts.api.OpsRunResponse;
import com.alarm.domain.model.OutboxEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** 运维接口：Outbox 触发与事件流水核对（版本一致性，R03 回归）。 */
@RestController
@RequestMapping("/api/v1/ops")
public class OpsController {

    private final OutboxRelay outboxRelay;
    private final OutboxRepository outboxRepository;

    public OpsController(OutboxRelay outboxRelay, OutboxRepository outboxRepository) {
        this.outboxRelay = outboxRelay;
        this.outboxRepository = outboxRepository;
    }

    @PostMapping("/outbox/run")
    public OpsRunResponse runOutbox(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId) {
        WebSupport.operator(operator, traceId);
        int processed = outboxRelay.runOnce();
        return new OpsRunResponse(processed, Instant.now().toString());
    }

    @GetMapping("/events/{aggregateId}")
    public List<EventLogItem> events(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @PathVariable("aggregateId") String aggregateId) {
        WebSupport.operator(operator, traceId);
        List<OutboxEvent> events = outboxRepository.listByAggregateId(aggregateId);
        return events.stream()
                .<EventLogItem>map(e -> new EventLogItem(e.getEventType(), e.getAggregateVersion(),
                        e.getIncidentId(), e.getCreatedAt() == null ? null : e.getCreatedAt().toString(),
                        e.getState().name()))
                .toList();
    }
}
