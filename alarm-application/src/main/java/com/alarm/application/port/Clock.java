package com.alarm.application.port;

import java.time.Instant;

/** 时间源端口；测试可注入可推进的假时钟。 */
public interface Clock {
    Instant now();
}
