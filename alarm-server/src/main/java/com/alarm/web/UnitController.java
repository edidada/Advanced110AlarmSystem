package com.alarm.web;

import com.alarm.application.service.AuditService;
import com.alarm.application.service.UnitService;
import com.alarm.application.support.OperatorContext;
import com.alarm.contracts.api.AuditEntryResponse;
import com.alarm.contracts.api.UnitUpsertRequest;
import com.alarm.contracts.api.UnitUpsertResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 警力 upsert 与审计查询。 */
@RestController
@RequestMapping("/api/v1")
public class UnitController {

    private final UnitService unitService;
    private final AuditService auditService;

    public UnitController(UnitService unitService, AuditService auditService) {
        this.unitService = unitService;
        this.auditService = auditService;
    }

    @PostMapping("/units")
    public UnitUpsertResponse upsert(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @RequestBody(required = false) UnitUpsertRequest request) {
        WebSupport.operator(operator, traceId);
        return unitService.upsert(request);
    }

    @GetMapping("/audits/{targetType}/{targetId}")
    public List<AuditEntryResponse> audits(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @PathVariable("targetType") String targetType,
            @PathVariable("targetId") String targetId) {
        WebSupport.operator(operator, traceId);
        return auditService.listByTarget(targetType.toUpperCase(), targetId);
    }
}
