package com.alarm.domain.model;

import com.alarm.contracts.enums.DispatchStatus;

import java.time.Instant;

/**
 * 派遣任务聚合。commandId 全局唯一，供终端幂等执行；
 * 同一警单下同一警力至多一条活动任务（ACTIVE_STATUSES）。
 */
public class DispatchTask {
    private String id;
    private String incidentId;
    private String unitId;
    private String commandId;
    private DispatchStatus status;
    private String note;
    private String assignedBy;
    private Instant deadline;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public DispatchTask() {
    }

    public DispatchTask(String id, String incidentId, String unitId, String commandId, DispatchStatus status,
                        String note, String assignedBy, Instant deadline, long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.unitId = unitId;
        this.commandId = commandId;
        this.status = status;
        this.note = note;
        this.assignedBy = assignedBy;
        this.deadline = deadline;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getUnitId() {
        return unitId;
    }

    public void setUnitId(String unitId) {
        this.unitId = unitId;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public DispatchStatus getStatus() {
        return status;
    }

    public void setStatus(DispatchStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(String assignedBy) {
        this.assignedBy = assignedBy;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public void setDeadline(Instant deadline) {
        this.deadline = deadline;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
