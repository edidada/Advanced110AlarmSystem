package com.alarm.contracts.common;

import com.alarm.contracts.enums.LocationSource;

import java.time.Instant;

/** 位置观测值：坐标语义由调用方约定（当前实现不换算坐标系），observedAt 为采集时间。 */
public record LocationDto(double x, double y, LocationSource source, Instant observedAt) {
    public LocationDto {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isInfinite(x) || Double.isInfinite(y)) {
            throw new IllegalArgumentException("location coordinates must be finite");
        }
    }
}
