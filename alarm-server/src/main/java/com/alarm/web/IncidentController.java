package com.alarm.web;

import com.alarm.application.service.IncidentService;
import com.alarm.application.service.UnitService;
import com.alarm.application.support.OperatorContext;
import com.alarm.contracts.api.CloseIncidentRequest;
import com.alarm.contracts.api.CloseIncidentResponse;
import com.alarm.contracts.api.CreateIncidentRequest;
import com.alarm.contracts.api.IncidentCreatedResponse;
import com.alarm.contracts.api.IncidentResponse;
import com.alarm.contracts.api.IncidentUpdatedResponse;
import com.alarm.contracts.api.RecommendationPreviewRequest;
import com.alarm.contracts.api.RecommendationPreviewResponse;
import com.alarm.contracts.api.UpdateIncidentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 警单与推荐接口。 */
@RestController
@RequestMapping("/api/v1")
public class IncidentController {

    private final IncidentService incidentService;
    private final UnitService unitService;

    public IncidentController(IncidentService incidentService, UnitService unitService) {
        this.incidentService = incidentService;
        this.unitService = unitService;
    }

    @PostMapping("/incidents")
    public ResponseEntity<IncidentCreatedResponse> create(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @RequestHeader(value = WebSupport.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CreateIncidentRequest request) {
        OperatorContext op = WebSupport.operator(operator, traceId);
        String key = WebSupport.requireIdempotencyKey(idempotencyKey);
        IncidentCreatedResponse response = incidentService.createIncident(op, key, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/incidents/{id}")
    public IncidentResponse get(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @PathVariable("id") String id) {
        WebSupport.operator(operator, traceId);
        return incidentService.getIncident(id);
    }

    @PatchMapping("/incidents/{id}")
    public IncidentUpdatedResponse patch(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @RequestHeader(value = WebSupport.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable("id") String id,
            @RequestBody(required = false) UpdateIncidentRequest request) {
        OperatorContext op = WebSupport.operator(operator, traceId);
        String key = WebSupport.requireIdempotencyKey(idempotencyKey);
        return incidentService.updateIncident(op, key, id, request);
    }

    @PostMapping("/incidents/{id}/close")
    public CloseIncidentResponse close(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @RequestHeader(value = WebSupport.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable("id") String id,
            @RequestBody(required = false) CloseIncidentRequest request) {
        OperatorContext op = WebSupport.operator(operator, traceId);
        String key = WebSupport.requireIdempotencyKey(idempotencyKey);
        return incidentService.closeIncident(op, key, id, request);
    }

    @PostMapping("/incidents/recommendations-preview")
    public RecommendationPreviewResponse recommendations(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @RequestBody(required = false) RecommendationPreviewRequest request) {
        WebSupport.operator(operator, traceId);
        return unitService.preview(request);
    }
}
