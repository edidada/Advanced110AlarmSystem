package com.alarm.application.port;

import com.alarm.domain.model.Incident;
import com.alarm.contracts.enums.Channel;
import com.alarm.contracts.enums.IncidentStatus;

import java.util.List;
import java.util.Optional;

/** 警单仓储端口；条件更新返回影响行数（0 即乐观锁冲突）。 */
public interface IncidentRepository {
    Optional<Incident> findById(String id);

    Optional<Incident> findByChannelAndSourceId(Channel channel, String sourceId);

    /**
     * 列表查询（GET /api/v1/incidents）：按状态可选过滤、创建时间倒序分页。
     * status 为 null 时不过滤；limit/offset 必须已由应用层校验（1..200，offset>=0）。
     */
    List<Incident> listPaged(IncidentStatus status, int limit, int offset);

    /** 列表总数（与 listPaged 同过滤口径）。 */
    long countPaged(IncidentStatus status);

    void insert(Incident incident);

    /**
     * 以 entity 当前字段整体覆写(WHERE id AND version=expectedRowVersion)，
     * 写入后 version = expectedRowVersion + 1。返回受影响行数。
     */
    int casUpdate(Incident incident, long expectedRowVersion);
}
