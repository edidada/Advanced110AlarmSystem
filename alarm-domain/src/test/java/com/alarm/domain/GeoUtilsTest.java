package com.alarm.domain;

import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.LocationSource;
import com.alarm.domain.geo.GeoUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilsTest {

    private static LocationDto at(double x, double y) {
        return new LocationDto(x, y, LocationSource.GPS, Instant.parse("2026-10-29T02:00:00Z"));
    }

    @Test
    void samePointIsZero() {
        assertThat(GeoUtils.distanceMeters(at(116.397, 39.909), at(116.397, 39.909))).isCloseTo(0.0, within(0.001));
    }

    @Test
    void oneDegreeLatitudeIsAbout111Km() {
        double d = GeoUtils.distanceMeters(at(116.0, 39.0), at(116.0, 40.0));
        assertThat(d).isCloseTo(111_195.0, within(500.0));
    }

    @Test
    void nearbyPairIsRoughly500m() {
        // ~0.0045 经度在赤道约为 500 米量级（用于推荐半径语义验证）
        double d = GeoUtils.distanceMeters(at(116.0, 39.9), at(116.0045, 39.9));
        assertThat(d).isBetween(300.0, 600.0);
    }
}
