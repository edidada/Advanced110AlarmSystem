package com.alarm.contracts.event;

/** 事件类型常量。schemaVersion 统一从 1 开始，兼容规则见详细设计文档。 */
public final class EventTypes {
    public static final String INCIDENT_CREATED = "IncidentCreated";
    public static final String INCIDENT_UPDATED = "IncidentUpdated";
    public static final String DISPATCH_CREATED = "DispatchCreated";
    public static final String DISPATCH_STATUS_CHANGED = "DispatchStatusChanged";
    public static final String INCIDENT_CLOSED = "IncidentClosed";

    private EventTypes() {
    }
}
