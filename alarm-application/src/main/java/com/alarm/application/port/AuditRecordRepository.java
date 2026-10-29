package com.alarm.application.port;

import com.alarm.domain.model.AuditRecord;

import java.util.List;

/** 审计仓储端口（追加写入）。 */
public interface AuditRecordRepository {
    void insert(AuditRecord record);

    List<AuditRecord> listByTarget(String targetType, String targetId);
}
