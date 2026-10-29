package com.alarm.contracts.enums;

/** 警单状态机：待核实 -&gt; 已受理 -&gt; 处置中 -&gt; 待复核 -&gt; 已结案；例外：已撤销。 */
public enum IncidentStatus {
    PENDING_VERIFICATION,
    ACCEPTED,
    DISPATCHING,
    PENDING_REVIEW,
    CLOSED,
    CANCELLED
}
