package com.alarm.application.support;

import com.alarm.application.port.AuditRecordRepository;
import com.alarm.domain.model.AuditRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** 审计写入与查询。detail 禁止包含敏感警情正文。 */
@Component
public class AuditWriter {

    public static final String TARGET_TYPE_INCIDENT = "INCIDENT";
    public static final String TARGET_TYPE_DISPATCH = "DISPATCH";
    public static final String TARGET_TYPE_UNIT = "UNIT";

    private final AuditRecordRepository repository;

    public AuditWriter(AuditRecordRepository repository) {
        this.repository = repository;
    }

    public interface AuditFields {
        String ACTOR_SYSTEM = "SYSTEM";
    }

    public void record(String actorId, String action, String targetType, String targetId, String detail, Instant occurredAt) {
        repository.insert(new AuditRecord(java.util.UUID.randomUUID().toString(),
                actorId, action, targetType, targetId, detail, occurredAt));
    }

    public List<AuditRecord> listByTarget(String targetType, String targetId) {
        return repository.listByTarget(targetType, targetId);
    }
}
