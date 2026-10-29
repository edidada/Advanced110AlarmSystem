package com.alarm.domain;

import com.alarm.contracts.enums.IncidentStatus;
import com.alarm.domain.exception.InvalidTransitionException;
import com.alarm.domain.rules.IncidentStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentStateMachineTest {

    @Test
    void pendingVerificationAcceptsVerifiedAndCancelled() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.PENDING_VERIFICATION, IncidentStatus.ACCEPTED))
                .isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.PENDING_VERIFICATION, IncidentStatus.CANCELLED))
                .isTrue();
    }

    @Test
    void pendingVerificationCannotDispatch() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.PENDING_VERIFICATION, IncidentStatus.DISPATCHING))
                .isFalse();
        assertThatThrownBy(() -> IncidentStateMachine.requireTransition(
                IncidentStatus.PENDING_VERIFICATION, IncidentStatus.DISPATCHING))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void acceptedGoesToDispatchingOrClosed() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.ACCEPTED, IncidentStatus.DISPATCHING)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.ACCEPTED, IncidentStatus.CLOSED)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.ACCEPTED, IncidentStatus.PENDING_VERIFICATION))
                .isFalse();
    }

    @Test
    void closedReopensOnlyToPendingReview() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.CLOSED, IncidentStatus.PENDING_REVIEW)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.CLOSED, IncidentStatus.DISPATCHING)).isFalse();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.CLOSED, IncidentStatus.CANCELLED)).isFalse();
    }

    @Test
    void sameStatusIsNotATransition() {
        IncidentStateMachine.requireTransition(IncidentStatus.DISPATCHING, IncidentStatus.DISPATCHING);
    }
}
