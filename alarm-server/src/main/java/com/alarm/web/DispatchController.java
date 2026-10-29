package com.alarm.web;

import com.alarm.application.service.DispatchService;
import com.alarm.application.support.OperatorContext;
import com.alarm.contracts.api.CancelDispatchRequest;
import com.alarm.contracts.api.CreateDispatchRequest;
import com.alarm.contracts.api.CreateDispatchResponse;
import com.alarm.contracts.api.OpsRunResponse;
import com.alarm.contracts.api.ReceiptRequest;
import com.alarm.contracts.api.ReceiptResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 派遣接口：创建、回执、取消、超时扫描。 */
@RestController
@RequestMapping("/api/v1")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/incidents/{id}/dispatches")
    public ResponseEntity<CreateDispatchResponse> createDispatches(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @RequestHeader(value = WebSupport.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable("id") String id,
            @RequestBody(required = false) CreateDispatchRequest request) {
        OperatorContext op = WebSupport.operator(operator, traceId);
        String key = WebSupport.requireIdempotencyKey(idempotencyKey);
        CreateDispatchResponse response = dispatchService.createDispatches(op, key, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/dispatches/{id}/receipts")
    public ReceiptResponse receipt(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @RequestHeader(value = WebSupport.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable("id") String id,
            @RequestBody(required = false) ReceiptRequest request) {
        OperatorContext op = WebSupport.operator(operator, traceId);
        String key = WebSupport.requireIdempotencyKey(idempotencyKey);
        return dispatchService.registerReceipt(op, id, request);
    }

    @PostMapping("/dispatches/{id}/cancel")
    public ReceiptResponse cancel(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId,
            @RequestHeader(value = WebSupport.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable("id") String id,
            @RequestBody(required = false) CancelDispatchRequest request) {
        OperatorContext op = WebSupport.operator(operator, traceId);
        String key = WebSupport.requireIdempotencyKey(idempotencyKey);
        return dispatchService.cancelDispatch(op, key, id, request);
    }

    /** 系统任务模拟：触发一次派遣超时扫描。 */
    @PostMapping("/ops/dispatch-timeout/run")
    public OpsRunResponse runTimeoutScan(
            @RequestHeader(value = WebSupport.HEADER_OPERATOR, required = false) String operator,
            @RequestHeader(value = WebSupport.HEADER_TRACE, required = false) String traceId) {
        OperatorContext op = WebSupport.operator(operator, traceId);
        int handled = dispatchService.runTimeoutScan(op);
        return new OpsRunResponse(handled, Instant.now().toString());
    }
}
