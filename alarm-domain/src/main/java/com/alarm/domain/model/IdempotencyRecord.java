package com.alarm.domain.model;

import java.time.Instant;

/** 幂等记录：(scope, key) 唯一；state 为 RESERVED/COMPLETED。 */
public class IdempotencyRecord {
    private String scope;
    private String key;
    private String operation;
    private String requestHash;
    private String responseBody;
    private Integer responseStatus;
    private IdempotencyState state;
    private Instant createdAt;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(String scope, String key, String operation, String requestHash,
                             String responseBody, Integer responseStatus, IdempotencyState state, Instant createdAt) {
        this.scope = scope;
        this.key = key;
        this.operation = operation;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.responseStatus = responseStatus;
        this.state = state;
        this.createdAt = createdAt;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public IdempotencyState getState() {
        return state;
    }

    public void setState(IdempotencyState state) {
        this.state = state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
