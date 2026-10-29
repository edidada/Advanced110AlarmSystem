package com.alarm.contracts.api;

import com.alarm.contracts.common.LocationDto;

/** POST /api/v1/incidents/recommendations-preview 请求体：incidentId 与 location 至少提供其一。 */
public record RecommendationPreviewRequest(String incidentId, LocationDto location, Integer radiusMeters) {
}
