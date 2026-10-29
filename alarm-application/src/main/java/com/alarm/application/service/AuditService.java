package com.alarm.application.service;

import com.alarm.application.support.AuditWriter;
import com.alarm.contracts.api.AuditEntryResponse;
import com.alarm.domain.model.AuditRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 审计查询用例（按目标检索）。 */
@Service
public class AuditService {

    private final AuditWriter auditWriter;

    public AuditService(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> listByTarget(String targetType, String targetId) {
        List<AuditRecord> records = auditWriter.listByTarget(targetType, targetId);
        return records.stream()
                .<AuditEntryResponse>map(r -> new AuditEntryResponse(
                        r.getId(), r.getActorId(), r.getAction(), r.getTargetType(),
                        r.getTargetId(), r.getDetail(), r.getOccurredAt()))
                .toList();
    }
}
