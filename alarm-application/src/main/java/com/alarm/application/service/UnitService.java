package com.alarm.application.service;

import com.alarm.application.port.Clock;
import com.alarm.application.port.IncidentRepository;
import com.alarm.application.port.UnitStatusRepository;
import com.alarm.contracts.api.RecommendationItem;
import com.alarm.contracts.api.RecommendationPreviewRequest;
import com.alarm.contracts.api.RecommendationPreviewResponse;
import com.alarm.contracts.api.UnitUpsertRequest;
import com.alarm.contracts.api.UnitUpsertResponse;
import com.alarm.contracts.common.LocationDto;
import com.alarm.domain.geo.GeoUtils;
import com.alarm.domain.model.Incident;
import com.alarm.domain.model.UnitStatus;
import com.alarm.domain.exception.IllegalRequestException;
import com.alarm.domain.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 警力用例：状态 upsert（按 unitId 幂等 + 乐观锁）与候选推荐（只读）。 */
@Service
public class UnitService {

    public static final int DEFAULT_RADIUS_METERS = 500;
    public static final int MAX_RADIUS_METERS = 10_000;

    private final UnitStatusRepository unitRepository;
    private final IncidentRepository incidentRepository;
    private final Clock clock;
    private final long locationStaleSeconds;

    public UnitService(UnitStatusRepository unitRepository,
                       IncidentRepository incidentRepository,
                       Clock clock,
                       @Value("${alarm.unit-location-stale-seconds:120}") long locationStaleSeconds) {
        this.unitRepository = unitRepository;
        this.incidentRepository = incidentRepository;
        this.clock = clock;
        this.locationStaleSeconds = locationStaleSeconds;
    }

    @Transactional
    public UnitUpsertResponse upsert(UnitUpsertRequest request) {
        validate(request);
        Instant now = clock.now();
        List<String> capabilities = request.capabilities() == null ? List.of() : request.capabilities();
        return unitRepository.findById(request.unitId())
                .<UnitUpsertResponse>map(existing -> {
                    UnitStatus merged = merged(existing, request, capabilities, now);
                    int rows = unitRepository.casUpdate(merged, existing.getVersion());
                    if (rows == 0) {
                        throw new com.alarm.domain.exception.OptimisticLockConflictException(existing.getVersion());
                    }
                    return new UnitUpsertResponse(request.unitId(), merged.getVersion());
                })
                .orElseGet(() -> {
                    UnitStatus unit = new UnitStatus(request.unitId(), request.name(),
                            capabilities, request.availability(), request.location(), null, 0L);
                    unitRepository.insert(unit);
                    return new UnitUpsertResponse(request.unitId(), 0L);
                });
    }

    /** 候选推荐：只读；中心点取 incident 定位或显式坐标；范围 (0, 10000] 米。 */
    @Transactional(readOnly = true)
    public RecommendationPreviewResponse preview(RecommendationPreviewRequest request) {
        if (request == null || (request.incidentId() == null && request.location() == null)) {
            throw new IllegalRequestException("incidentId or location is required");
        }
        int radius = request.radiusMeters() == null ? DEFAULT_RADIUS_METERS : request.radiusMeters();
        if (radius <= 0 || radius > MAX_RADIUS_METERS) {
            throw new IllegalRequestException("radiusMeters must be within (0," + MAX_RADIUS_METERS + "]");
        }
        LocationDto center;
        if (request.incidentId() != null) {
            Incident incident = incidentRepository.findById(request.incidentId())
                    .orElseThrow(() -> new NotFoundException("incident", request.incidentId()));
            if (incident.getLocation() == null) {
                throw new IllegalRequestException("incident has no location observation");
            }
            center = incident.getLocation();
        } else {
            center = request.location();
        }
        Instant now = clock.now();
        List<RecommendationItem> items = new ArrayList<>();
        for (UnitStatus unit : unitRepository.listAll()) {
            if (unit.getLocation() == null || unit.getLocation().observedAt() == null) {
                continue;
            }
            double distance = GeoUtils.distanceMeters(center, unit.getLocation());
            if (distance > radius) {
                continue;
            }
            List<String> warnings = new ArrayList<>();
            if (unit.getAvailability() <= 0) {
                warnings.add("UNIT_UNAVAILABLE");
            }
            if (Duration.between(unit.getLocation().observedAt(), now).getSeconds() > locationStaleSeconds) {
                warnings.add("UNIT_LOCATION_STALE");
            }
            items.add(new RecommendationItem(unit.getUnitId(), distance,
                    unit.getAvailability(), unit.getLocation().observedAt(), warnings));
        }
        items.sort(Comparator.comparingDouble(RecommendationItem::distanceMeters));
        return new RecommendationPreviewResponse(items);
    }

    private void validate(UnitUpsertRequest request) {
        if (request == null || request.unitId() == null || request.unitId().isBlank()) {
            throw new IllegalRequestException("unitId is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalRequestException("name is required");
        }
        if (request.availability() == null || request.availability() < 0 || request.availability() > 100) {
            throw new IllegalRequestException("availability must be within [0,100]");
        }
        if (request.location() != null && request.location().observedAt() == null) {
            throw new IllegalRequestException("location.observedAt is required when location provided");
        }
    }

    private UnitStatus merged(UnitStatus existing, UnitUpsertRequest request, List<String> capabilities, Instant now) {
        UnitStatus merged = new UnitStatus(existing.getUnitId(), request.name(),
                capabilities, request.availability(), request.location(),
                existing.getLastDispatchId(), existing.getVersion());
        // upsert 不改写 lastDispatchId，保持已绑定的派遣关联
        return merged;
    }
}
