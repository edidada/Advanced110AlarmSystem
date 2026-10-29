package com.alarm.domain.model;

import java.time.Instant;

/** 审计记录，追加写入；不含明文口令与完整敏感正文。 */
public class AuditRecord {
    private String id;
    private String actorId;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private Instant occurredAt;

    public AuditRecord() {
    }

    public AuditRecord(String id, String actorId, String action, String targetType, String targetId,
                       String detail, Instant occurredAt) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
