package com.alarm.domain;

import com.alarm.contracts.enums.DispatchStatus;
import com.alarm.domain.exception.InvalidTransitionException;
import com.alarm.domain.rules.DispatchStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatchStateMachineTest {

    @Test
    void pendingConfirmTransitionsToTerminalAndActive() {
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.PENDING_CONFIRM, DispatchStatus.ACCEPTED)).isTrue();
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.PENDING_CONFIRM, DispatchStatus.REJECTED)).isTrue();
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.PENDING_CONFIRM, DispatchStatus.TIMEOUT)).isTrue();
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.PENDING_CONFIRM, DispatchStatus.CANCELLED)).isTrue();
        // 不允许跨级出发
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.PENDING_CONFIRM, DispatchStatus.ENROUTE)).isFalse();
        assertThatThrownBy(() -> DispatchStateMachine.requireTransition(
                DispatchStatus.PENDING_CONFIRM, DispatchStatus.ENROUTE))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void chainThroughFeedbackDone() {
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.ACCEPTED, DispatchStatus.ENROUTE)).isTrue();
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.ENROUTE, DispatchStatus.ARRIVED)).isTrue();
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.ARRIVED, DispatchStatus.FEEDBACK_DONE)).isTrue();
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.FEEDBACK_DONE, DispatchStatus.CANCELLED))
                .isFalse();
    }

    @Test
    void terminalStatusesAreRecognized() {
        for (DispatchStatus status : DispatchStatus.values()) {
            boolean terminal = status == DispatchStatus.FEEDBACK_DONE
                    || status == DispatchStatus.REJECTED
                    || status == DispatchStatus.TIMEOUT
                    || status == DispatchStatus.CANCELLED;
            assertThat(DispatchStateMachine.isTerminal(status)).as(status.name()).isEqualTo(terminal);
            assertThat(DispatchStateMachine.isActive(status)).as(status.name()).isEqualTo(!terminal);
        }
    }

    @Test
    void lateAcceptAfterTimeoutDenied() {
        assertThat(DispatchStateMachine.canTransition(DispatchStatus.TIMEOUT, DispatchStatus.ACCEPTED)).isFalse();
    }
}
