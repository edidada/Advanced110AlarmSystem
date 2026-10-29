package com.alarm.contracts.enums;

/** PATCH /incidents/{id} 的动作类型。 */
public enum VerifyAction {
    /** 核实并受理：PENDING_VERIFICATION -&gt; ACCEPTED，必填 reason 与 verifiedAddress。 */
    VERIFY,
    /** 普通字段修改，不改变状态。 */
    UPDATE,
    /** 撤销警单：PENDING_VERIFICATION -&gt; CANCELLED，必填 reason，须先终止活动派遣。 */
    CANCEL,
    /** 重开：CLOSED -&gt; PENDING_REVIEW；PENDING_REVIEW -&gt; DISPATCHING（重新派遣前）。 */
    REOPEN
}
