package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.WorkflowNodeRunEntity;
import com.raglaw.agentscope.domain.WorkflowNodeRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable idempotency boundary backed by the workflow node-run table. */
@Service
public class JpaWorkflowRunStore implements WorkflowRunStore {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private final WorkflowNodeRunRepository repository;
    private final ObjectMapper objectMapper;

    public JpaWorkflowRunStore(WorkflowNodeRunRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public synchronized StartAttemptResult startAttempt(NodeAttemptCommand command) {
        validate(command.runId(), command.nodeCode(), command.idempotencyKey());
        WorkflowNodeRunEntity existing = find(command.runId(), command.nodeCode(), command.idempotencyKey());
        if (existing != null) {
            ensureSamePayload(existing, command.payloadHash());
            if (isTerminal(existing.getStatus())) return new StartAttemptResult(command.idempotencyKey(), true, false);
            Instant now = Instant.now();
            if (existing.getLeaseUntil() != null && existing.getLeaseUntil().isAfter(now)
                    && !command.ownerId().equals(existing.getOwnerId())) {
                return new StartAttemptResult(command.idempotencyKey(), false, false);
            }
            existing.setOwnerId(command.ownerId());
            existing.setLeaseUntil(now.plus(LEASE));
            existing.setStatus("RUNNING");
            repository.save(existing);
            return new StartAttemptResult(command.idempotencyKey(), false, true);
        }
        WorkflowNodeRunEntity row = new WorkflowNodeRunEntity();
        row.setId(UUID.randomUUID().toString());
        row.setRunId(command.runId());
        row.setNodeCode(command.nodeCode());
        row.setAttempt(1);
        row.setStatus("RUNNING");
        row.setIdempotencyKey(command.idempotencyKey());
        row.setPayloadHash(command.payloadHash());
        row.setSnapshotVersion(command.snapshotVersion());
        row.setStartedAt(Instant.now());
        row.setOutputRevision(0L);
        row.setOwnerId(command.ownerId());
        row.setLeaseUntil(Instant.now().plus(LEASE));
        repository.save(row);
        return new StartAttemptResult(command.idempotencyKey(), false, true);
    }

    @Override
    @Transactional
    public synchronized NodeExecutionResult completeAttempt(CompleteAttemptCommand command) {
        validate(command.runId(), command.nodeCode(), command.idempotencyKey());
        WorkflowNodeRunEntity row = find(command.runId(), command.nodeCode(), command.idempotencyKey());
        if (row == null) {
            row = new WorkflowNodeRunEntity();
            row.setId(UUID.randomUUID().toString());
            row.setRunId(command.runId());
            row.setNodeCode(command.nodeCode());
            row.setAttempt(1);
            row.setIdempotencyKey(command.idempotencyKey());
            row.setPayloadHash(command.payloadHash());
            row.setSnapshotVersion(command.snapshotVersion());
            row.setStartedAt(Instant.now());
            row.setOwnerId(command.ownerId());
        } else {
            ensureSamePayload(row, command.payloadHash());
            if (isTerminal(row.getStatus()) && row.getStructuredOutputJson() != null) return toResult(row);
            if (row.getOwnerId() != null && command.ownerId() != null && !command.ownerId().equals(row.getOwnerId())) {
                throw new IllegalStateException("workflow node lease owned by another coordinator");
            }
        }
        row.setStatus(command.status());
        row.setStructuredOutputJson(write(command.output()));
        row.setEvidenceIdsJson(String.join(",", command.evidenceIds() == null ? List.of() : command.evidenceIds()));
        row.setCompletedAt(Instant.now());
        row.setOutputRevision((row.getOutputRevision() == null ? 0L : row.getOutputRevision()) + 1L);
        row.setRoleCode(command.roleCode());
        row.setAgentCode(command.agentCode());
        row.setAgentVersion(command.agentVersion());
        row.setNodeSessionId(command.nodeSessionId());
        row.setInputRefsJson(write(command.inputRefs()));
        row.setToolCallSummaryJson(write(command.toolCallSummary()));
        row.setLeaseUntil(null);
        repository.save(row);
        return toResult(row);
    }

    @Override
    @Transactional
    public synchronized boolean renewLease(NodeAttemptCommand command) {
        validate(command.runId(), command.nodeCode(), command.idempotencyKey());
        WorkflowNodeRunEntity row = find(command.runId(), command.nodeCode(), command.idempotencyKey());
        if (row == null || isTerminal(row.getStatus()) || !command.ownerId().equals(row.getOwnerId())) return false;
        ensureSamePayload(row, command.payloadHash());
        row.setLeaseUntil(Instant.now().plus(LEASE));
        repository.save(row);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NodeExecutionResult> findAccepted(NodeAttemptCommand command) {
        validate(command.runId(), command.nodeCode(), command.idempotencyKey());
        WorkflowNodeRunEntity row = find(command.runId(), command.nodeCode(), command.idempotencyKey());
        if (row == null || !isTerminal(row.getStatus()) || row.getStructuredOutputJson() == null) return Optional.empty();
        ensureSamePayload(row, command.payloadHash());
        return Optional.of(toResult(row));
    }

    private WorkflowNodeRunEntity find(String runId, String nodeCode, String key) {
        return repository.findByRunIdAndNodeCodeAndIdempotencyKey(runId, nodeCode, key).orElse(null);
    }

    private NodeExecutionResult toResult(WorkflowNodeRunEntity row) {
        return new NodeExecutionResult(row.getNodeCode(), row.getIdempotencyKey(), row.getStatus(),
                read(row.getStructuredOutputJson()), split(row.getEvidenceIdsJson()),
                row.getSnapshotVersion() == null ? 0L : row.getSnapshotVersion());
    }

    private String write(Map<String, Object> output) {
        try { return objectMapper.writeValueAsString(output == null ? Map.of() : output); }
        catch (Exception e) { throw new IllegalArgumentException("workflow output is not serializable", e); }
    }

    private Map<String, Object> read(String output) {
        if (output == null || output.isBlank()) return Map.of();
        try { return objectMapper.readValue(output, OBJECT_MAP); }
        catch (Exception e) { return Map.of("raw", output); }
    }

    private List<String> split(String values) {
        if (values == null || values.isBlank()) return List.of();
        return java.util.Arrays.stream(values.split(",")).filter(value -> !value.isBlank()).toList();
    }

    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)
                || "TIMED_OUT".equals(status) || "RETRY_EXHAUSTED".equals(status);
    }

    private static void ensureSamePayload(WorkflowNodeRunEntity row, String payloadHash) {
        if (row.getPayloadHash() != null && !row.getPayloadHash().equals(payloadHash)) {
            throw new IdempotencyConflictException("idempotency key payload mismatch");
        }
    }

    private static void validate(String runId, String nodeCode, String key) {
        if (runId == null || runId.isBlank() || nodeCode == null || nodeCode.isBlank()
                || key == null || key.isBlank()) throw new IllegalArgumentException("attempt identity");
    }
}
