package com.alarm.application.port;

import com.alarm.domain.model.UnitStatus;

import java.util.List;
import java.util.Optional;

/** 警力状态仓储端口。 */
public interface UnitStatusRepository {
    Optional<UnitStatus> findById(String unitId);

    List<UnitStatus> listAll();

    void insert(UnitStatus unit);

    int casUpdate(UnitStatus unit, long expectedRowVersion);

    /** 原子预占：availability >= amount 才扣减并标记 lastDispatchId；返回是否成功。 */
    boolean tryReserve(String unitId, String dispatchId, int amount);

    /** 释放（任务终态）；availability 上限 100。 */
    void release(String unitId, int amount);
}
