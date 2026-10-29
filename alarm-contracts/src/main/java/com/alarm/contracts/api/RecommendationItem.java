package com.alarm.contracts.api;

import java.time.Instant;
import java.util.List;

/** 单个推荐候选。 */
public record RecommendationItem(
        String unitId,
        double distanceMeters,
        int availability,
        Instant observedAt,
        List<String> warnings) {
    public RecommendationItem {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
