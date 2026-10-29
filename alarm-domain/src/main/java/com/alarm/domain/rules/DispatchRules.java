package com.alarm.domain.rules;

import java.util.Set;

/** 派遣相关业务常量。 */
public final class DispatchRules {
    /** 创建派遣时从警力可用额度中预占的数量（详细设计文档 3.4）。 */
    public static final int RESERVE_AMOUNT = 20;
    /** 缺省接单时限（秒）。 */
    public static final int DEFAULT_DEADLINE_SECONDS = 60;
    /** 接单时限允许范围（秒）。 */
    public static final int MIN_DEADLINE_SECONDS = 30;
    public static final int MAX_DEADLINE_SECONDS = 600;
    /** 单次派遣允许的警力数量范围。 */
    public static final int MIN_UNITS_PER_DISPATCH = 1;
    public static final int MAX_UNITS_PER_DISPATCH = 5;

    private DispatchRules() {
    }
}
