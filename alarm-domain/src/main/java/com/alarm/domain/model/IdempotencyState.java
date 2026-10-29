package com.alarm.domain.model;

/** 幂等记录状态。RESERVED 与业务写入同事务，提交后即 COMPLETED。 */
public enum IdempotencyState {
    RESERVED,
    COMPLETED
}
