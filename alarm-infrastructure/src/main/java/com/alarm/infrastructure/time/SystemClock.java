package com.alarm.infrastructure.time;

import com.alarm.application.port.Clock;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 当前默认时钟实现（生产），可测试替换。 */
@Component
public class SystemClock implements Clock {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
