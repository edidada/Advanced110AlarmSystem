package com.alarm.domain.geo;

import com.alarm.contracts.common.LocationDto;

/** 球面距离估算（Haversine），仅用于推荐排序与半径圈选，不用于执法定位证据。 */
public final class GeoUtils {
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    public static double distanceMeters(LocationDto a, LocationDto b) {
        double lat1 = Math.toRadians(a.y());
        double lat2 = Math.toRadians(b.y());
        double dLat = Math.toRadians(b.y() - a.y());
        double dLon = Math.toRadians(b.x() - a.x());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    private GeoUtils() {
    }
}
