package com.alarm.application.port;

import com.alarm.domain.model.Incident;
import com.alarm.contracts.enums.Channel;

import java.util.Optional;

/** 警单仓储端口；条件更新返回影响行数（0 即乐观锁冲突）。 */
public interface IncidentRepository {
    Optional<Incident> findById(String id);

    Optional<Incident> findByChannelAndSourceId(Channel channel, String sourceId);

    void insert(Incident incident);

    /**
     * 以 entity 当前字段整体覆写(WHERE id AND version=expectedRowVersion)，
     * 写入后 version = expectedRowVersion + 1。返回受影响行数。
     */
    int casUpdate(Incident incident, long expectedRowVersion);
}
