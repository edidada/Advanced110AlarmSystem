package com.alarm.contracts.api;

import java.util.List;

/** POST /api/v1/incidents/recommendations-preview 响应体，按距离升序。 */
public record RecommendationPreviewResponse(List<RecommendationItem> items) {
    public RecommendationPreviewResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
