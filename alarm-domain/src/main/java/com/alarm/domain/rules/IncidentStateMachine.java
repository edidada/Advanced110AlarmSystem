package com.alarm.domain.rules;

import com.alarm.contracts.enums.IncidentStatus;

import java.util.Map;
import java.util.Set;

/**
 * 警单状态机。非法迁移抛 InvalidTransitionException（409 ALM-1003）。
 * 与详细设计文档 3.2 一致。
 */
public final class IncidentStateMachine {

    private static final Map<IncidentStatus, Set<IncidentStatus>> TRANSITIONS = Map.of(
            IncidentStatus.PENDING_VERIFICATION, Set.of(IncidentStatus.ACCEPTED, IncidentStatus.CANCELLED),
            // 已受理（尚无派遣任务）可直接结案，或进入处置中
            IncidentStatus.ACCEPTED, Set.of(IncidentStatus.DISPATCHING, IncidentStatus.CLOSED),
            IncidentStatus.DISPATCHING, Set.of(IncidentStatus.PENDING_REVIEW),
            IncidentStatus.PENDING_REVIEW, Set.of(IncidentStatus.CLOSED, IncidentStatus.DISPATCHING),
            // 重开：已结案 -> 待复核
            IncidentStatus.CLOSED, Set.of(IncidentStatus.PENDING_REVIEW),
            IncidentStatus.CANCELLED, Set.of()
    );

    public static boolean canTransition(IncidentStatus from, IncidentStatus to) {
        Set<IncidentStatus> next = TRANSITIONS.get(from);
        return next != null && next.contains(to);
    }

    public static void requireTransition(IncidentStatus from, IncidentStatus to) {
        if (from == to) {
            return; // 同状态保持（如 DISPATCHING 下追加派遣）不是迁移
        }
        if (!canTransition(from, to)) {
            throw new com.alarm.domain.exception.InvalidTransitionException(
                    "INCIDENT_STATUS_TRANSITION_DENIED", from, to);
        }
    }

    public static Set<IncidentStatus> nextStates(IncidentStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    private IncidentStateMachine() {
    }
}
