package com.alarm.domain.rules;

import com.alarm.contracts.enums.DispatchStatus;

import java.util.Map;
import java.util.Set;

/**
 * 派遣任务状态机，与详细设计文档 3.3 一致。
 * 终态：FEEDBACK_DONE / REJECTED / TIMEOUT / CANCELLED。
 */
public final class DispatchStateMachine {

    private static final Map<DispatchStatus, Set<DispatchStatus>> TRANSITIONS = Map.of(
            DispatchStatus.PENDING_CONFIRM, Set.of(DispatchStatus.ACCEPTED, DispatchStatus.REJECTED,
                    DispatchStatus.TIMEOUT, DispatchStatus.CANCELLED),
            DispatchStatus.ACCEPTED, Set.of(DispatchStatus.ENROUTE, DispatchStatus.CANCELLED),
            DispatchStatus.ENROUTE, Set.of(DispatchStatus.ARRIVED, DispatchStatus.CANCELLED),
            DispatchStatus.ARRIVED, Set.of(DispatchStatus.FEEDBACK_DONE),
            DispatchStatus.FEEDBACK_DONE, Set.of(),
            DispatchStatus.REJECTED, Set.of(),
            DispatchStatus.TIMEOUT, Set.of(),
            DispatchStatus.CANCELLED, Set.of()
    );

    public static boolean isTerminal(DispatchStatus status) {
        return TRANSITIONS.getOrDefault(status, Set.of()).isEmpty();
    }

    public static boolean isActive(DispatchStatus status) {
        return !isTerminal(status);
    }

    public static boolean canTransition(DispatchStatus from, DispatchStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(DispatchStatus from, DispatchStatus to) {
        if (!canTransition(from, to)) {
            throw new com.alarm.domain.exception.InvalidTransitionException(
                    "DISPATCH_STATUS_TRANSITION_DENIED", from, to);
        }
    }

    public static Set<DispatchStatus> nextStates(DispatchStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    private DispatchStateMachine() {
    }
}
