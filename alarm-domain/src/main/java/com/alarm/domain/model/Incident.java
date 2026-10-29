package com.alarm.domain.model;

import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.Channel;
import com.alarm.contracts.enums.IncidentStatus;
import com.alarm.contracts.enums.Priority;

import java.time.Instant;

/**
 * 警单聚合（纯 POJO，由仓储层映射）。
 * 不变量由状态机与用例层保证：(channel, sourceId) 全局唯一；version 单调递增。
 */
public class Incident {
    private String id;
    private Channel channel;
    private String sourceId;
    private IncidentStatus status;
    private Priority priority;
    private String type;
    private String description;
    private String verifiedAddress;
    /** 原始定位观测值，与人工核实地址分开保存；迟到定位不得覆盖 verifiedAddress。 */
    private LocationDto location;
    private String conclusion;
    private String createdBy;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

    public Incident() {
    }

    public Incident(String id, Channel channel, String sourceId, IncidentStatus status, Priority priority,
                    String type, String description, String verifiedAddress, LocationDto location,
                    String createdBy, long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.channel = channel;
        this.sourceId = sourceId;
        this.status = status;
        this.priority = priority;
        this.type = type;
        this.description = description;
        this.verifiedAddress = verifiedAddress;
        this.location = location;
        this.createdBy = createdBy;
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

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVerifiedAddress() {
        return verifiedAddress;
    }

    public void setVerifiedAddress(String verifiedAddress) {
        this.verifiedAddress = verifiedAddress;
    }

    public LocationDto getLocation() {
        return location;
    }

    public void setLocation(LocationDto location) {
        this.location = location;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}
