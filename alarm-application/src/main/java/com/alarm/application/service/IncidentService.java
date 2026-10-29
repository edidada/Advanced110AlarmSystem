package com.alarm.application.service;

import com.alarm.application.port.Clock;
import com.alarm.application.port.DispatchTaskRepository;
import com.alarm.application.port.IdGenerator;
import com.alarm.application.port.IncidentRepository;
import com.alarm.contracts.api.CloseIncidentRequest;
import com.alarm.contracts.api.CloseIncidentResponse;
import com.alarm.contracts.api.CreateIncidentRequest;
import com.alarm.contracts.api.IncidentCreatedResponse;
import com.alarm.contracts.api.IncidentResponse;
import com.alarm.contracts.api.IncidentUpdatedResponse;
import com.alarm.contracts.api.UpdateIncidentRequest;
import com.alarm.contracts.common.LocationDto;
import com.alarm.contracts.enums.IncidentStatus;
import com.alarm.contracts.enums.VerifyAction;
import com.alarm.contracts.event.EventTypes;
import com.alarm.contracts.event.payload.IncidentClosedPayload;
import com.alarm.contracts.event.payload.IncidentCreatedPayload;
import com.alarm.contracts.event.payload.IncidentUpdatedPayload;
import com.alarm.application.support.AuditWriter;
import com.alarm.application.support.EventRecorder;
import com.alarm.application.support.IdempotencyExecutor;
import com.alarm.application.support.Mappers;
import com.alarm.application.support.OperatorContext;
import com.alarm.domain.model.DispatchTask;
import com.alarm.domain.model.Incident;
import com.alarm.domain.rules.DispatchStateMachine;
import com.alarm.domain.rules.IncidentStateMachine;
import com.alarm.domain.exception.IllegalRequestException;
import com.alarm.domain.exception.NotFoundException;
import com.alarm.domain.exception.OptimisticLockConflictException;
import com.alarm.domain.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 警单用例：登记（防重复建单）、核实/修改/撤销/重开、结案。 */
@Service
public class IncidentService {

    public static final String OP_CREATE = "CREATE_INCIDENT";
    public static final String OP_UPDATE = "UPDATE_INCIDENT";
    public static final String OP_CLOSE = "CLOSE_INCIDENT";

    private final IncidentRepository incidentRepository;
    private final DispatchTaskRepository dispatchTaskRepository;
    private final IdempotencyExecutor idempotency;
    private final EventRecorder events;
    private final AuditWriter audits;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public IncidentService(IncidentRepository incidentRepository,
                           DispatchTaskRepository dispatchTaskRepository,
                           IdempotencyExecutor idempotency,
                           EventRecorder events,
                           AuditWriter audits,
                           Clock clock,
                           IdGenerator idGenerator) {
        this.incidentRepository = incidentRepository;
        this.dispatchTaskRepository = dispatchTaskRepository;
        this.idempotency = idempotency;
        this.events = events;
        this.audits = audits;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /** 登记警单：同 (channel, sourceId) 幂等返回既有警单；Outbox 的 IncidentCreated 与建单同事务。 */
    @Transactional
    public IncidentCreatedResponse createIncident(OperatorContext op, String idempotencyKey, CreateIncidentRequest request) {
        validateCreate(request);
        String scope = idempotencyScope(op, OP_CREATE);
        return idempotency.execute(scope, idempotencyKey, OP_CREATE, request, IncidentCreatedResponse.class,
                () -> {
                    Optional<Incident> existing = incidentRepository
                            .findByChannelAndSourceId(request.channel(), request.sourceId());
                    if (existing.isPresent()) {
                        Incident found = existing.get();
                        return new IncidentCreatedResponse(found.getId(), found.getStatus(), found.getVersion());
                    }
                    Instant now = clock.now();
                    Incident incident = new Incident(
                            idGenerator.nextId(), request.channel(), request.sourceId(),
                            IncidentStatus.PENDING_VERIFICATION, request.priority(), request.type(),
                            trim(request.description()), null, request.location(), op.operatorId(),
                            0L, now, now);
                    incidentRepository.insert(incident);
                    events.record(EventTypes.INCIDENT_CREATED, AuditWriter.TARGET_TYPE_INCIDENT,
                            incident.getId(), incident.getVersion(), incident.getId(), op.traceId(),
                            new IncidentCreatedPayload(request.channel(), request.sourceId(),
                                    request.priority(), request.type()));
                    audits.record(op.operatorId(), "INCIDENT_CREATED", AuditWriter.TARGET_TYPE_INCIDENT,
                            incident.getId(), "channel=" + request.channel() + ",sourceId=" + request.sourceId(), now);
                    return new IncidentCreatedResponse(incident.getId(), incident.getStatus(), incident.getVersion());
                });
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(String incidentId) {
        Incident incident = load(incidentId);
        List<DispatchTask> tasks = dispatchTaskRepository.listByIncidentId(incident.getId());
        return Mappers.toIncidentResponse(incident, tasks);
    }

    /** 核实/修改/撤销/重开；条件更新 expectedVersion，冲突返回 409。幂等作用域含目标警单 id，防止跨对象键撞车。 */
    @Transactional
    public IncidentUpdatedResponse updateIncident(OperatorContext op, String idempotencyKey,
                                                  String incidentId, UpdateIncidentRequest request) {
        if (request == null || request.action() == null) {
            throw new IllegalRequestException("action is required");
        }
        Instant now = clock.now();
        VerifyAction action = request.action();
        String scope = idempotencyScope(op, action.name() + "_INCIDENT:" + incidentId);
        return idempotency.execute(scope, idempotencyKey, OP_UPDATE + ":" + action.name(),
                request, IncidentUpdatedResponse.class, () -> {
                    Incident incident = load(incidentId);
                    long expected = request.expectedVersion();
                    List<String> changed = new ArrayList<>();

                    Incident target = new Incident();
                    copyForUpdate(incident, target);

                    if (request.priority() != null) {
                        target.setPriority(request.priority());
                        changed.add("priority");
                    }
                    if (request.type() != null) {
                        target.setType(request.type());
                        changed.add("type");
                    }
                    if (request.description() != null) {
                        target.setDescription(trim(request.description()));
                        changed.add("description");
                    }
                    if (request.verifiedAddress() != null) {
                        target.setVerifiedAddress(trim(request.verifiedAddress()));
                        changed.add("verifiedAddress");
                    }
                    if (request.location() != null) {
                        target.setLocation(request.location());
                        changed.add("location");
                    }

                    IncidentStatus nextStatus = incident.getStatus();
                    switch (action) {
                        case VERIFY -> {
                            requireReason(action, request.reason());
                            if (target.getVerifiedAddress() == null || target.getVerifiedAddress().isBlank()) {
                                throw new ValidationException("verifiedAddress is required to VERIFY an incident");
                            }
                            nextStatus = IncidentStatus.ACCEPTED;
                        }
                        case CANCEL -> {
                            requireReason(action, request.reason());
                            nextStatus = IncidentStatus.CANCELLED;
                        }
                        case REOPEN -> {
                            requireReason(action, request.reason());
                            nextStatus = incident.getStatus() == IncidentStatus.CLOSED
                                    ? IncidentStatus.PENDING_REVIEW
                                    : IncidentStatus.DISPATCHING;
                        }
                        case UPDATE -> nextStatus = incident.getStatus();
                    }

                    if (nextStatus != incident.getStatus()) {
                        int activeTasks = dispatchTaskRepository.countActiveByIncidentId(incident.getId());
                        if (activeTasks > 0) {
                            throw new com.alarm.domain.exception.InvalidTransitionException(
                                    "ACTIVE_DISPATCH_EXISTS",
                                    "terminate active dispatch tasks before incident " + action.name());
                        }
                        IncidentStateMachine.requireTransition(incident.getStatus(), nextStatus);
                    }

                    target.setStatus(nextStatus);
                    target.setVersion(expected + 1);
                    target.setUpdatedAt(now);
                    if (nextStatus == IncidentStatus.CLOSED || nextStatus == IncidentStatus.CANCELLED) {
                        target.setClosedAt(now);
                    }

                    int rows = incidentRepository.casUpdate(target, expected);
                    if (rows == 0) {
                        throw new OptimisticLockConflictException(expected);
                    }

                    events.record(EventTypes.INCIDENT_UPDATED, AuditWriter.TARGET_TYPE_INCIDENT,
                            incident.getId(), target.getVersion(), incident.getId(), op.traceId(),
                            new IncidentUpdatedPayload(action, nextStatus, changed, request.reason()));
                    audits.record(op.operatorId(), "INCIDENT_" + action.name(), AuditWriter.TARGET_TYPE_INCIDENT,
                            incident.getId(), "status=" + nextStatus + ",reason=" + request.reason(), now);
                    return new IncidentUpdatedResponse(incident.getId(), nextStatus, target.getVersion());
                });
    }

    /** 结案：要求 ACCEPTED（无派遣任务）或 PENDING_REVIEW（无活动任务），并提供复核结论。幂等作用域含目标警单 id。 */
    @Transactional
    public CloseIncidentResponse closeIncident(OperatorContext op, String idempotencyKey,
                                               String incidentId, CloseIncidentRequest request) {
        if (request == null || request.conclusion() == null || request.conclusion().isBlank()) {
            throw new ValidationException("conclusion is required to close an incident");
        }
        String scope = idempotencyScope(op, OP_CLOSE + ":" + incidentId);
        return idempotency.execute(scope, idempotencyKey, OP_CLOSE, request, CloseIncidentResponse.class,
                () -> {
                    Incident incident = load(incidentId);
                    long expected = request.expectedVersion();
                    if (incident.getStatus() != IncidentStatus.ACCEPTED
                            && incident.getStatus() != IncidentStatus.PENDING_REVIEW) {
                        throw new com.alarm.domain.exception.InvalidTransitionException(
                                incident.getStatus(), IncidentStatus.CLOSED);
                    }
                    int activeTasks = dispatchTaskRepository.countActiveByIncidentId(incident.getId());
                    if (activeTasks > 0) {
                        throw new com.alarm.domain.exception.InvalidTransitionException(
                                "ACTIVE_DISPATCH_EXISTS",
                                "terminate active dispatch tasks before closing incident");
                    }
                    Instant now = clock.now();
                    Incident target = new Incident();
                    copyForUpdate(incident, target);
                    target.setStatus(IncidentStatus.CLOSED);
                    target.setConclusion(trim(request.conclusion()));
                    target.setClosedAt(now);
                    target.setVersion(expected + 1);
                    target.setUpdatedAt(now);
                    int rows = incidentRepository.casUpdate(target, expected);
                    if (rows == 0) {
                        throw new OptimisticLockConflictException(expected);
                    }
                    events.record(EventTypes.INCIDENT_CLOSED, AuditWriter.TARGET_TYPE_INCIDENT,
                            incident.getId(), target.getVersion(), incident.getId(), op.traceId(),
                            new IncidentClosedPayload(request.conclusion(), op.operatorId(), now.toString()));
                    audits.record(op.operatorId(), "INCIDENT_CLOSED", AuditWriter.TARGET_TYPE_INCIDENT,
                            incident.getId(), "conclusion=" + request.conclusion(), now);
                    return new CloseIncidentResponse(incident.getId(), IncidentStatus.CLOSED, target.getVersion());
                });
    }

    private void validateCreate(CreateIncidentRequest request) {
        if (request == null || request.channel() == null) {
            throw new IllegalRequestException("channel is required");
        }
        if (isBlank(request.sourceId()) || request.sourceId().length() > 64) {
            throw new IllegalRequestException("sourceId is required, max length 64");
        }
        if (request.priority() == null) {
            throw new IllegalRequestException("priority is required");
        }
        if (isBlank(request.type()) || request.type().length() > 32) {
            throw new IllegalRequestException("type is required, max length 32");
        }
        if (request.description() != null && request.description().length() > 512) {
            throw new IllegalRequestException("description max length 512");
        }
        validateLocation(request.location());
    }

    private void validateLocation(LocationDto location) {
        if (location == null) {
            return;
        }
        if (location.source() == null) {
            throw new IllegalRequestException("location.source is required");
        }
        Instant observedAt = location.observedAt();
        if (observedAt == null || observedAt.toEpochMilli() < 0) {
            throw new IllegalRequestException("location.observedAt must be a valid instant after epoch");
        }
    }

    private void requireReason(VerifyAction action, String reason) {
        if (isBlank(reason)) {
            throw new ValidationException("reason is required for " + action + " action");
        }
    }

    Incident load(String incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("incident", incidentId));
    }

    /** 统一复制的静态入口：基于已提交事实构建可写副本（包内外均可调用）。 */
    static void copyForUpdate(Incident from, Incident to) {
        to.setId(from.getId());
        to.setChannel(from.getChannel());
        to.setSourceId(from.getSourceId());
        to.setStatus(from.getStatus());
        to.setPriority(from.getPriority());
        to.setType(from.getType());
        to.setDescription(from.getDescription());
        to.setVerifiedAddress(from.getVerifiedAddress());
        to.setLocation(from.getLocation());
        to.setConclusion(from.getConclusion());
        to.setCreatedBy(from.getCreatedBy());
        to.setVersion(from.getVersion());
        to.setCreatedAt(from.getCreatedAt());
        to.setUpdatedAt(from.getUpdatedAt());
        to.setClosedAt(from.getClosedAt());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public static String idempotencyScope(OperatorContext op, String operation) {
        return op.operatorId() + ":" + operation;
    }

    /** 供 DispatchService 复用：活动状态校验不需要 FULL 状态机校验，仅此处判断任务终态。 */
    static boolean hasActiveTasks(DispatchTaskRepository repository, String incidentId) {
        return repository.countActiveByIncidentId(incidentId) > 0;
    }
}
