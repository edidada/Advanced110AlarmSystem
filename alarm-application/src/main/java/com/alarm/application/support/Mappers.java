package com.alarm.application.support;

import com.alarm.contracts.api.CreatedDispatchItem;
import com.alarm.contracts.api.DispatchSummary;
import com.alarm.contracts.api.IncidentResponse;
import com.alarm.domain.model.DispatchTask;
import com.alarm.domain.model.Incident;

import java.util.List;

/** 领域模型 -> contracts DTO 映射。 */
public final class Mappers {

    public static IncidentResponse toIncidentResponse(Incident incident, List<DispatchTask> tasks) {
        return new IncidentResponse(
                incident.getId(),
                incident.getChannel(),
                incident.getSourceId(),
                incident.getStatus(),
                incident.getPriority(),
                incident.getType(),
                incident.getDescription(),
                incident.getVerifiedAddress(),
                incident.getLocation(),
                incident.getConclusion(),
                incident.getCreatedBy(),
                incident.getVersion(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getClosedAt(),
                tasks.stream().map(Mappers::toDispatchSummary).toList());
    }

    public static DispatchSummary toDispatchSummary(DispatchTask task) {
        return new DispatchSummary(task.getId(), task.getUnitId(), task.getStatus(), task.getVersion());
    }

    public static CreatedDispatchItem toCreatedDispatchItem(DispatchTask task) {
        return new CreatedDispatchItem(task.getId(), task.getUnitId(), task.getCommandId(),
                task.getStatus(), task.getDeadline(), task.getVersion());
    }

    private Mappers() {
    }
}
