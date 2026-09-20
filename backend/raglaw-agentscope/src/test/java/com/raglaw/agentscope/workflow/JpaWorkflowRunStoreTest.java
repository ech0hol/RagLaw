package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.WorkflowNodeRunEntity;
import com.raglaw.agentscope.domain.WorkflowNodeRunRepository;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaWorkflowRunStoreTest {
    @Test
    void repeatedCompletionReturnsAcceptedResultWithoutDuplicateState() {
        WorkflowNodeRunRepository repository = repository();
        JpaWorkflowRunStore store = new JpaWorkflowRunStore(repository, new ObjectMapper());
        var first = new WorkflowRunStore.CompleteAttemptCommand("run-1", "law", "key-1", "hash", "SUCCEEDED", Map.of("answer", "ok"), List.of(), 18);
        NodeExecutionResult result1 = store.completeAttempt(first);
        JpaWorkflowRunStore restarted = new JpaWorkflowRunStore(repository, new ObjectMapper());
        NodeExecutionResult result2 = restarted.completeAttempt(first);
        assertThat(result2).isEqualTo(result1);
    }

    @Test
    void duplicateKeyWithDifferentPayloadIsRejected() {
        WorkflowNodeRunRepository repository = repository();
        JpaWorkflowRunStore store = new JpaWorkflowRunStore(repository, new ObjectMapper());
        store.completeAttempt(new WorkflowRunStore.CompleteAttemptCommand("run-1", "law", "key-1", "hash", "SUCCEEDED", Map.of(), List.of(), 18));
        assertThatThrownBy(() -> store.completeAttempt(new WorkflowRunStore.CompleteAttemptCommand("run-1", "law", "key-1", "other", "SUCCEEDED", Map.of(), List.of(), 18)))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void startAttemptDoesNotRerunAcceptedKey() {
        WorkflowNodeRunRepository repository = repository();
        JpaWorkflowRunStore store = new JpaWorkflowRunStore(repository, new ObjectMapper());
        var command = new WorkflowRunStore.NodeAttemptCommand("run-1", "law", "key-1", "hash", 18);
        assertThat(store.startAttempt(command).alreadyAccepted()).isFalse();
        assertThat(store.startAttempt(command).alreadyAccepted()).isFalse();
        store.completeAttempt(new WorkflowRunStore.CompleteAttemptCommand("run-1", "law", "key-1", "hash", "SUCCEEDED", Map.of(), List.of(), 18));
        assertThat(store.startAttempt(command).alreadyAccepted()).isTrue();
    }

    @Test
    void activeLeasePreventsAnotherCoordinatorFromCallingTheAgent() {
        WorkflowNodeRunRepository repository = repository();
        JpaWorkflowRunStore store = new JpaWorkflowRunStore(repository, new ObjectMapper());
        var first = new WorkflowRunStore.NodeAttemptCommand("run-lease", "law", "key-1", "hash", 18, "owner-a");
        var second = new WorkflowRunStore.NodeAttemptCommand("run-lease", "law", "key-1", "hash", 18, "owner-b");
        assertThat(store.startAttempt(first).leaseAcquired()).isTrue();
        assertThat(store.startAttempt(second).leaseAcquired()).isFalse();
        assertThat(store.startAttempt(second).alreadyAccepted()).isFalse();
    }

    private WorkflowNodeRunRepository repository() {
        WorkflowNodeRunRepository repository = mock(WorkflowNodeRunRepository.class);
        Map<String, WorkflowNodeRunEntity> rows = new HashMap<>();
        when(repository.findByRunIdAndNodeCodeAndIdempotencyKey(any(), any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(rows.get(key(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)))));
        when(repository.save(any())).thenAnswer(invocation -> {
            WorkflowNodeRunEntity row = invocation.getArgument(0);
            if (row.getId() == null) row.setId(UUID.randomUUID().toString());
            rows.put(key(row.getRunId(), row.getNodeCode(), row.getIdempotencyKey()), row);
            return row;
        });
        return repository;
    }

    private String key(String runId, String nodeCode, String idempotencyKey) {
        return runId + "|" + nodeCode + "|" + idempotencyKey;
    }
}
