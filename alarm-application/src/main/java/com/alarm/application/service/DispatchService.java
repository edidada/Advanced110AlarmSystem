package com.alarm.application.service;

import com.alarm.application.port.Clock;
import com.alarm.application.port.DispatchTaskRepository;
import com.alarm.application.port.IdGenerator;
import com.alarm.application.port.IncidentRepository;
import com.alarm.application.port.UnitStatusRepository;
import com.alarm.contracts.api.CancelDispatchRequest;
import com.alarm.contracts.api.CreateDispatchRequest;
import com.alarm.contracts.api.CreateDispatchResponse;
import com.alarm.contracts.api.CreatedDispatchItem;
import com.alarm.contracts.api.ReceiptRequest;
import com.alarm.contracts.api.ReceiptResponse;
import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.DispatchStatus;
import com.alarm.contracts.enums.IncidentStatus;
import com.alarm.contracts.enums.VerifyAction;
import com.alarm.contracts.event.EventTypes;
import com.alarm.contracts.event.payload.DispatchCreatedPayload;
import com.alarm.contracts.event.payload.DispatchStatusChangedPayload;
import com.alarm.application.support.AuditWriter;
import com.alarm.application.support.EventRecorder;
import com.alarm.application.support.IdempotencyExecutor;
import com.alarm.application.support.Mappers;
import com.alarm.application.support.OperatorContext;
import com.alarm.domain.model.DispatchTask;
import com.alarm.domain.model.Incident;
import com.alarm.domain.model.UnitStatus;
import com.alarm.domain.rules.DispatchRules;
import com.alarm.domain.rules.DispatchStateMachine;
import com.alarm.domain.exception.IllegalRequestException;
import com.alarm.domain.exception.NotFoundException;
import com.alarm.domain.exception.OptimisticLockConflictException;
import com.alarm.domain.exception.ValidationException;
import com.alarm.domain.exception.InvalidTransitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 派遣用例：创建任务（含警力原子预占）、终端回执（commandId 幂等）、取消、超时扫描。 */
@Service
public class DispatchService {

    public static final String OP_CREATE_DISPATCH = "CREATE_DISPATCH";
    public static final String OP_RECEIPT = "DISPATCH_RECEIPT";
    public static final String OP_CANCEL_DISPATCH = "CANCEL_DISPATCH";
    public static final String WARNING_UNIT_LOCATION_STALE = "UNIT_LOCATION_STALE";

    private final IncidentService incidentService;
    private final IncidentRepository incidentRepository;
    private final DispatchTaskRepository dispatchRepository;
    private final UnitStatusRepository unitRepository;
    private final IdempotencyExecutor idempotency;
    private final EventRecorder events;
    private final AuditWriter audits;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final long locationStaleSeconds;
    private final int defaultDeadlineSeconds;

    public DispatchService(IncidentService incidentService,
                           IncidentRepository incidentRepository,
                           DispatchTaskRepository dispatchRepository,
                           UnitStatusRepository unitRepository,
                           IdempotencyExecutor idempotency,
                           EventRecorder events,
                           AuditWriter audits,
                           Clock clock,
                           IdGenerator idGenerator,
                           @Value("${alarm.unit-location-stale-seconds:120}") long locationStaleSeconds,
                           @Value("${alarm.dispatch-default-deadline-seconds:60}") int defaultDeadlineSeconds) {
        this.incidentService = incidentService;
        this.incidentRepository = incidentRepository;
        this.dispatchRepository = dispatchRepository;
        this.unitRepository = unitRepository;
        this.idempotency = idempotency;
        this.events = events;
        this.audits = audits;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.locationStaleSeconds = locationStaleSeconds;
        this.defaultDeadlineSeconds = defaultDeadlineSeconds;
    }

    /** 创建派遣：状态机校验、同警力唯一活动任务、原子预占额度、Incident 版本同步推进。 */
    @Transactional
    public CreateDispatchResponse createDispatches(OperatorContext op, String idempotencyKey,
                                                   String incidentId, CreateDispatchRequest request) {
        if (request == null || request.unitIds() == null || request.unitIds().isEmpty()) {
            throw new IllegalRequestException("unitIds is required");
        }
        String scope = IncidentService.idempotencyScope(op, OP_CREATE_DISPATCH + ":" + incidentId);
        return idempotency.execute(scope, idempotencyKey, OP_CREATE_DISPATCH, request,
                CreateDispatchResponse.class, () -> doCreateDispatches(op, incidentId, request));
    }

    private CreateDispatchResponse doCreateDispatches(OperatorContext op, String incidentId,
                                                      CreateDispatchRequest request) {
        Incident incident = incidentService.load(incidentId);
        if (incident.getStatus() != IncidentStatus.ACCEPTED && incident.getStatus() != IncidentStatus.DISPATCHING) {
            throw new InvalidTransitionException("DISPATCH_NOT_ALLOWED", incident.getStatus(), IncidentStatus.DISPATCHING);
        }

        Set<String> unitIds = new LinkedHashSet<>(request.unitIds());
        if (unitIds.size() != request.unitIds().size()) {
            throw new ValidationException("unitIds contains duplicates");
        }
        if (unitIds.size() < DispatchRules.MIN_UNITS_PER_DISPATCH
                || unitIds.size() > DispatchRules.MAX_UNITS_PER_DISPATCH) {
            throw new ValidationException("unitIds size must be between "
                    + DispatchRules.MIN_UNITS_PER_DISPATCH + " and " + DispatchRules.MAX_UNITS_PER_DISPATCH);
        }
        int deadlineSeconds = request.deadlineSeconds() == null ? defaultDeadlineSeconds : request.deadlineSeconds();
        if (deadlineSeconds < DispatchRules.MIN_DEADLINE_SECONDS
                || deadlineSeconds > DispatchRules.MAX_DEADLINE_SECONDS) {
            throw new ValidationException("deadlineSeconds must be between "
                    + DispatchRules.MIN_DEADLINE_SECONDS + " and " + DispatchRules.MAX_DEADLINE_SECONDS);
        }

        Instant now = clock.now();
        Instant deadline = now.plus(Duration.ofSeconds(deadlineSeconds));
        List<String> warnings = new ArrayList<>();
        List<CreatedDispatchItem> items = new ArrayList<>();

        for (String unitId : unitIds) {
            UnitStatus unit = unitRepository.findById(unitId)
                    .orElseThrow(() -> new NotFoundException("unit", unitId));
            if (dispatchRepository.existsActiveByIncidentAndUnit(incidentId, unitId)) {
                throw new InvalidTransitionException("UNIT_ALREADY_DISPATCHED",
                        "unit already has an active dispatch task in this incident: " + unitId);
            }
            String dispatchId = idGenerator.nextId();
            if (!unitRepository.tryReserve(unitId, dispatchId, DispatchRules.RESERVE_AMOUNT)) {
                throw new InvalidTransitionException("UNIT_UNAVAILABLE",
                        "unit availability insufficient: " + unitId);
            }
            LocationDto loc = unit.getLocation();
            if (loc != null && loc.observedAt() != null
                    && Duration.between(loc.observedAt(), now).getSeconds() > locationStaleSeconds) {
                warnings.add(WARNING_UNIT_LOCATION_STALE + ":" + unitId);
            }
            DispatchTask task = new DispatchTask(dispatchId, incidentId, unitId, idGenerator.nextId(),
                    DispatchStatus.PENDING_CONFIRM, request.note(), op.operatorId(), deadline,
                    0L, now, now);
            dispatchRepository.insert(task);
            items.add(Mappers.toCreatedDispatchItem(task));
            events.record(EventTypes.DISPATCH_CREATED, AuditWriter.TARGET_TYPE_DISPATCH,
                    task.getId(), task.getVersion(), incidentId, op.traceId(),
                    new DispatchCreatedPayload(task.getId(), unitId, task.getCommandId(), deadline));
        }

        // 警单推进：首次派遣 ACCEPTED -> DISPATCHING；版本无条件推进以维持事件聚合版本
        boolean transition = incident.getStatus() == IncidentStatus.ACCEPTED;
        IncidentStatus nextStatus = transition ? IncidentStatus.DISPATCHING : incident.getStatus();
        Incident target = new Incident();
        IncidentService.copyForUpdate(incident, target);
        target.setStatus(nextStatus);
        target.setVersion(request.expectedVersion() + 1);
        target.setUpdatedAt(now);
        int rows = incidentRepository.casUpdate(target, request.expectedVersion());
        if (rows == 0) {
            throw new OptimisticLockConflictException(request.expectedVersion());
        }

        audits.record(op.operatorId(), "DISPATCH_CREATED", AuditWriter.TARGET_TYPE_INCIDENT,
                incidentId, "units=" + unitIds + ",note=" + request.note(), now);
        return new CreateDispatchResponse(target.getVersion(), items, warnings);
    }

    /** 终端回执：同 dispatchId+commandId+targetState 幂等；迟到接单拒绝；终态释放警力额度。 */
    @Transactional
    public ReceiptResponse registerReceipt(OperatorContext op, String dispatchId, ReceiptRequest request) {
        if (request == null || request.targetStatus() == null) {
            throw new IllegalRequestException("targetStatus is required");
        }
        if (request.commandId() == null || request.commandId().isBlank()) {
            throw new IllegalRequestException("commandId is required");
        }
        String scope = IncidentService.idempotencyScope(op, OP_RECEIPT + ":" + dispatchId);
        String key = request.commandId() + ":" + request.targetStatus();
        return idempotency.execute(scope, key, OP_RECEIPT, request, ReceiptResponse.class,
                () -> doRegisterReceipt(op, dispatchId, request));
    }

    private ReceiptResponse doRegisterReceipt(OperatorContext op, String dispatchId, ReceiptRequest request) {
        DispatchTask task = loadTask(dispatchId);
        if (!task.getCommandId().equals(request.commandId())) {
            throw new IllegalRequestException("commandId mismatch for dispatch " + dispatchId);
        }
        if (request.targetStatus() == DispatchStatus.TIMEOUT) {
            // TIMEOOUT 仅由系统超时扫描产生，终端不得自声明
            throw new InvalidTransitionException("TIMEOUT_BY_SYSTEM_ONLY",
                    task.getStatus(), DispatchStatus.TIMEOUT);
        }
        DispatchStatus from = task.getStatus();
        DispatchStatus to = request.targetStatus();
        DispatchStateMachine.requireTransition(from, to);
        Instant now = clock.now();

        if (to == DispatchStatus.ACCEPTED && from == DispatchStatus.PENDING_CONFIRM
                && now.isAfter(task.getDeadline())) {
            throw new InvalidTransitionException("DEADLINE_PASSED", from, to);
        }

        DispatchTask updatedTask = updated(task, to, now);
        int rows = dispatchRepository.casUpdate(updatedTask, request.expectedVersion());
        if (rows == 0) {
            throw new OptimisticLockConflictException(request.expectedVersion());
        }
        task.setStatus(to);
        task.setVersion(request.expectedVersion() + 1);

        applyTerminalConsequences(op, task, from, to, now);
        return new ReceiptResponse(dispatchId, to, task.getVersion());
    }

    /** 取消派遣（非终态可取消；理由必填）。 */
    @Transactional
    public ReceiptResponse cancelDispatch(OperatorContext op, String idempotencyKey, String dispatchId,
                                          CancelDispatchRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new ValidationException("reason is required to cancel a dispatch task");
        }
        String scope = IncidentService.idempotencyScope(op, OP_CANCEL_DISPATCH + ":" + dispatchId);
        return idempotency.execute(scope, idempotencyKey, OP_CANCEL_DISPATCH, request, ReceiptResponse.class,
                () -> {
                    DispatchTask task = loadTask(dispatchId);
                    DispatchStatus from = task.getStatus();
                    if (DispatchStateMachine.isTerminal(from)) {
                        throw new InvalidTransitionException("ALREADY_TERMINAL", from, DispatchStatus.CANCELLED);
                    }
                    DispatchStateMachine.requireTransition(from, DispatchStatus.CANCELLED);
                    Instant now = clock.now();
                    DispatchTask updatedTask = updated(task, DispatchStatus.CANCELLED, now);
                    int rows = dispatchRepository.casUpdate(updatedTask, request.expectedVersion());
                    if (rows == 0) {
                        throw new OptimisticLockConflictException(request.expectedVersion());
                    }
                    task.setStatus(DispatchStatus.CANCELLED);
                    task.setVersion(request.expectedVersion() + 1);
                    applyTerminalConsequences(op, task, from, DispatchStatus.CANCELLED, now);
                    return new ReceiptResponse(dispatchId, DispatchStatus.CANCELLED, task.getVersion());
                });
    }

    /** 超时扫描：PENDING_CONFIRM 且 deadline 已过 -> TIMEOUT（恶意重放须经审计重放工具，此处为系统任务）。 */
    @Transactional
    public int runTimeoutScan(OperatorContext op) {
        Instant now = clock.now();
        List<DispatchTask> expired = dispatchRepository.findExpiredPending(now);
        int handled = 0;
        for (DispatchTask task : expired) {
            int rows = dispatchRepository.casUpdate(updated(task, DispatchStatus.TIMEOUT, now), task.getVersion());
            if (rows == 0) {
                continue; // 并发修改（回执/取消），跳过本轮，下轮再核
            }
            task.setStatus(DispatchStatus.TIMEOUT);
            task.setVersion(task.getVersion() + 1);
            applyTerminalConsequences(op, task, DispatchStatus.PENDING_CONFIRM, DispatchStatus.TIMEOUT, now);
            handled++;
        }
        return handled;
    }

    private void applyTerminalConsequences(OperatorContext op, DispatchTask task,
                                           DispatchStatus from, DispatchStatus to, Instant now) {
        if (DispatchStateMachine.isTerminal(to)) {
            unitRepository.release(task.getUnitId(), DispatchRules.RESERVE_AMOUNT);
        }
        events.record(EventTypes.DISPATCH_STATUS_CHANGED, AuditWriter.TARGET_TYPE_DISPATCH,
                task.getId(), task.getVersion(), task.getIncidentId(), op.traceId(),
                new DispatchStatusChangedPayload(task.getId(), task.getUnitId(), from, to, task.getNote()));
        audits.record(op.operatorId(), "DISPATCH_STATUS_CHANGED", AuditWriter.TARGET_TYPE_DISPATCH,
                task.getId(), "from=" + from + ",to=" + to, now);

        if (DispatchStateMachine.isTerminal(to)) {
            promoteIfAllTerminated(op, task, now);
        }
    }

    private void promoteIfAllTerminated(OperatorContext op, DispatchTask task, Instant now) {
        Incident incident = incidentService.load(task.getIncidentId());
        if (incident.getStatus() == IncidentStatus.DISPATCHING
                && dispatchRepository.countActiveByIncidentId(incident.getId()) == 0) {
            IncidentStatus next = IncidentStatus.PENDING_REVIEW;
            Incident target = new Incident();
            IncidentService.copyForUpdate(incident, target);
            target.setStatus(next);
            target.setVersion(incident.getVersion() + 1);
            target.setUpdatedAt(now);
            int rows = incidentRepository.casUpdate(target, incident.getVersion());
            if (rows == 0) {
                throw new OptimisticLockConflictException(incident.getVersion());
            }
            events.record(EventTypes.INCIDENT_UPDATED, AuditWriter.TARGET_TYPE_INCIDENT,
                    incident.getId(), target.getVersion(), incident.getId(), op.traceId(),
                    new com.alarm.contracts.event.payload.IncidentUpdatedPayload(
                            VerifyAction.UPDATE, next, List.of("status"), "all dispatch tasks terminal"));
        }
    }

    private DispatchTask updated(DispatchTask task, DispatchStatus to, Instant now) {
        DispatchTask merged = shallowCopy(task);
        merged.setStatus(to);
        merged.setVersion(task.getVersion() + 1);
        merged.setUpdatedAt(now);
        return merged;
    }

    private DispatchTask shallowCopy(DispatchTask from) {
        return new DispatchTask(from.getId(), from.getIncidentId(), from.getUnitId(), from.getCommandId(),
                from.getStatus(), from.getNote(), from.getAssignedBy(), from.getDeadline(),
                from.getVersion(), from.getCreatedAt(), from.getUpdatedAt());
    }

    private DispatchTask loadTask(String dispatchId) {
        return dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new NotFoundException("dispatch", dispatchId));
    }

    /** 内嵌校验辅助（保持包内可见，替代引入额外依赖）。 */
    static final class IncidentStateMachineCheck {
        static void assertIncidentAcceptsDispatch(IncidentStatus status) {
            if (status == IncidentStatus.CANCELLED || status == IncidentStatus.CLOSED
                    || status == IncidentStatus.PENDING_VERIFICATION
                    || status == IncidentStatus.PENDING_REVIEW) {
                throw new InvalidTransitionException("DISPATCH_NOT_ALLOWED", status, IncidentStatus.DISPATCHING);
            }
        }
    }
}
