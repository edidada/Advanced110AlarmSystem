package com.alarm.infrastructure.id;

import com.alarm.application.port.IdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** UUID 生成实现。 */
@Component
public class UuidIdGenerator implements IdGenerator {
    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
