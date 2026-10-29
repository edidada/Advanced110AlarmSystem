package com.alarm.contracts.enums;

/** 派遣任务状态机：待确认 -&gt; 已接单 -&gt; 已出发 -&gt; 已到场 -&gt; 已反馈；例外：已拒绝/已超时/已取消。 */
public enum DispatchStatus {
    PENDING_CONFIRM,
    ACCEPTED,
    ENROUTE,
    ARRIVED,
    FEEDBACK_DONE,
    REJECTED,
    TIMEOUT,
    CANCELLED
}
