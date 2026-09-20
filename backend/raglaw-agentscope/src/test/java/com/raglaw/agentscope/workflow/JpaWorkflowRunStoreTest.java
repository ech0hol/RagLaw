package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JpaWorkflowRunStoreTest {
    @Test
    void repeatedCompletionReturnsAcceptedResultWithoutDuplicateState() {
        JpaWorkflowRunStore store = new JpaWorkflowRunStore();
        var first = new WorkflowRunStore.CompleteAttemptCommand("run-1", "law", "key-1", "hash", "SUCCEEDED", Map.of("answer", "ok"), List.of(), 18);
        NodeExecutionResult result1 = store.completeAttempt(first);
        NodeExecutionResult result2 = store.completeAttempt(first);
        assertThat(result1).isSameAs(result2);
    }

    @Test
    void duplicateKeyWithDifferentPayloadIsRejected() {
        JpaWorkflowRunStore store = new JpaWorkflowRunStore();
        store.completeAttempt(new WorkflowRunStore.CompleteAttemptCommand("run-1", "law", "key-1", "hash", "SUCCEEDED", Map.of(), List.of(), 18));
        assertThatThrownBy(() -> store.completeAttempt(new WorkflowRunStore.CompleteAttemptCommand("run-1", "law", "key-1", "other", "SUCCEEDED", Map.of(), List.of(), 18)))
                .isInstanceOf(IdempotencyConflictException.class);
    }
}
