package com.alarm.application.port;

import com.alarm.domain.model.DispatchTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 派遣任务仓储端口。 */
public interface DispatchTaskRepository {
    Optional<DispatchTask> findById(String id);

    List<DispatchTask> listByIncidentId(String incidentId);

    /** 统计警单下活动（非终态）任务数量。 */
    int countActiveByIncidentId(String incidentId);

    /** 指定警单下指定警力是否存在活动任务。 */
    boolean existsActiveByIncidentAndUnit(String incidentId, String unitId);

    List<DispatchTask> findExpiredPending(Instant now);

    void insert(DispatchTask task);

    int casUpdate(DispatchTask task, long expectedRowVersion);
}
