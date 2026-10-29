package com.alarm.domain.model;

import java.time.Instant;

/** Outbox 事件行；payload 为完整事件信封 JSON。 */
public class OutboxEvent {
    private String eventId;
    private String eventType;
    private String incidentId;
    private String aggregateType;
    private String aggregateId;
    private long aggregateVersion;
    private String payload;
    private String traceId;
    private OutboxState state;
    private Instant createdAt;
    private Instant publishedAt;

    public OutboxEvent() {
    }

    public OutboxEvent(String eventId, String eventType, String incidentId, String aggregateType,
                       String aggregateId, long aggregateVersion, String payload, String traceId,
                       OutboxState state, Instant createdAt, Instant publishedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.incidentId = incidentId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.payload = payload;
        this.traceId = traceId;
        this.state = state;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public void setAggregateVersion(long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public OutboxState getState() {
        return state;
    }

    public void setState(OutboxState state) {
        this.state = state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
