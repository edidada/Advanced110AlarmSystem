package com.alarm.web;

import com.alarm.application.service.OutboxRelay;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Outbox 定时补发调度。 */
@Component
public class OutboxRelayScheduler {

    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    /** 每 5 秒一轮；失败事件留在 PENDING，下轮继续限速补发。 */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void run() {
        relay.runOnce();
    }
}
