package cn.finalscompass.ai.runtime.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeTraceStateMachineTest {
    private final RuntimeTraceStateMachine states = new RuntimeTraceStateMachine();

    @Test
    void allowsWaitingResumeAndSuccess() {
        assertDoesNotThrow(() -> states.requireTransition(
                RuntimeExecutionStatus.RUNNING, RuntimeExecutionStatus.WAITING_USER));
        assertDoesNotThrow(() -> states.requireTransition(
                RuntimeExecutionStatus.WAITING_USER, RuntimeExecutionStatus.RUNNING));
        assertDoesNotThrow(() -> states.requireTransition(
                RuntimeExecutionNodeStatus.RUNNING, RuntimeExecutionNodeStatus.SUCCEEDED));
    }

    @Test
    void rejectsTerminalTransitionsAndSkippedExecutionState() {
        assertThrows(IllegalStateException.class, () -> states.requireTransition(
                RuntimeExecutionStatus.SUCCEEDED, RuntimeExecutionStatus.RUNNING));
        assertThrows(IllegalStateException.class, () -> states.requireTransition(
                RuntimeExecutionNodeStatus.SKIPPED, RuntimeExecutionNodeStatus.RUNNING));
        assertThrows(IllegalStateException.class, () -> states.requireTransition(
                RuntimeExecutionStatus.CREATED, RuntimeExecutionStatus.SUCCEEDED));
        assertThrows(IllegalStateException.class, () -> states.requireTransition(
                RuntimeProviderInvocationStatus.SUCCEEDED, RuntimeProviderInvocationStatus.RUNNING));
    }

    @Test
    void allowsProviderInvocationLifecycle() {
        assertDoesNotThrow(() -> states.requireTransition(
                RuntimeProviderInvocationStatus.ACCEPTED, RuntimeProviderInvocationStatus.RUNNING));
        assertDoesNotThrow(() -> states.requireTransition(
                RuntimeProviderInvocationStatus.RUNNING, RuntimeProviderInvocationStatus.TIMEOUT));
    }
}
