package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.WorkflowConflictRepository;
import com.raglaw.agentscope.domain.WorkflowNodeRunEntity;
import com.raglaw.agentscope.domain.WorkflowNodeRunRepository;
import com.raglaw.agentscope.domain.WorkflowRunEntity;
import com.raglaw.agentscope.domain.WorkflowRunRepository;
import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.service.CaseMemorySnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Single-writer coordinator around the DAG executor. It makes node acceptance durable
 * before a result can be observed as reusable, while keeping model/tool execution outside
 * the database transaction.
 */
@Service
public class PersistentWorkflowCoordinator {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final long LEASE_WAIT_MILLIS = 5_000L;
    private static final long LEASE_HEARTBEAT_MILLIS = 30_000L;
    private final WorkflowRunRepository runs;
    private final WorkflowNodeRunRepository nodes;
    private final WorkflowConflictRepository conflicts;
    private final WorkflowRunStore attemptStore;
    private final WorkflowExecutor executor;
    private final ObjectMapper objectMapper;
    private final WorkflowResultReducer reducer;
    private final String ownerId = UUID.randomUUID().toString();

    public record StartWorkflowCommand(WorkflowRunEntity run, WorkflowDefinition workflow, String traceId,
                                       String input, WorkflowNodeRunner runner) {}
    public record ResumeWorkflowCommand(String runId, WorkflowDefinition workflow, String traceId,
                                        String input, WorkflowNodeRunner runner) {}
    public record WorkflowRunOutcome(String status, String runId, WorkflowExecutor.ExecutionResult execution,
                                     String errorCode) {}

    public PersistentWorkflowCoordinator(WorkflowRunRepository runs, WorkflowNodeRunRepository nodes,
                                         WorkflowConflictRepository conflicts, WorkflowRunStore attemptStore,
                                         WorkflowExecutor executor, ObjectMapper objectMapper) {
        this(runs, nodes, conflicts, attemptStore, executor, objectMapper, new WorkflowResultReducer());
    }

    @Autowired
    public PersistentWorkflowCoordinator(WorkflowRunRepository runs, WorkflowNodeRunRepository nodes,
                                         WorkflowConflictRepository conflicts, WorkflowRunStore attemptStore,
                                         WorkflowExecutor executor, ObjectMapper objectMapper,
                                         WorkflowResultReducer reducer) {
        this.runs = runs;
        this.nodes = nodes;
        this.conflicts = conflicts;
        this.attemptStore = attemptStore;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.reducer = reducer;
    }

    public WorkflowRunOutcome start(StartWorkflowCommand command) {
        require(command == null ? null : command.run(), "workflow run");
        require(command.workflow(), "workflow");
        require(command.runner(), "runner");
        WorkflowRunEntity run = command.run();
        if (isTerminal(run.getStatus())) return new WorkflowRunOutcome(run.getStatus(), run.getId(), null, run.getErrorCode());
        run.setStatus("RUNNING");
        if (run.getStartedAt() == null) run.setStartedAt(Instant.now());
        runs.save(run);
        return execute(run, command.workflow(), command.traceId(), command.input(), command.runner().bindRunId(run.getId()), Map.of());
    }

    public WorkflowRunOutcome resume(ResumeWorkflowCommand command) {
        require(command, "resume command");
        require(command.workflow(), "workflow");
        require(command.runner(), "runner");
        WorkflowRunEntity run = runs.findById(command.runId()).orElseThrow(() -> new IllegalArgumentException("workflow run not found"));
        if (isTerminal(run.getStatus())) return new WorkflowRunOutcome(run.getStatus(), run.getId(), null, run.getErrorCode());
        validatePersistedBinding(run, command.workflow(), command.runner());
        if (hasNonSuccessfulTerminal(run.getId())) {
            run.setStatus("FAILED");
            run.setErrorCode("WORKFLOW_NODE_FAILED");
            run.setCompletedAt(Instant.now());
            runs.save(run);
            return new WorkflowRunOutcome("FAILED", run.getId(), null, "WORKFLOW_NODE_FAILED");
        }
        run.setStatus("RUNNING");
        runs.save(run);
        return execute(run, command.workflow(), command.traceId(), command.input(), command.runner().bindRunId(run.getId()), loadCompleted(run.getId()));
    }

    /** Executes a run row that was created by the routing boundary before this coordinator was invoked. */
    public WorkflowRunOutcome executeExisting(WorkflowRunEntity run, WorkflowDefinition workflow,
                                              String traceId, String input, WorkflowNodeRunner runner) {
        require(run, "workflow run");
        require(workflow, "workflow");
        require(runner, "runner");
        WorkflowNodeRunner bound = runner.bindRunId(run.getId());
        validatePersistedBinding(run, workflow, bound);
        return execute(run, workflow, traceId, input, bound, loadCompleted(run.getId()));
    }

    private WorkflowRunOutcome execute(WorkflowRunEntity run, WorkflowDefinition workflow, String traceId,
                                       String input, WorkflowNodeRunner runner, Map<String, WorkflowNodeResult> completed) {
        WorkflowNodeRunner durableRunner = (node, context) -> executeNode(run, node, context, runner);
        WorkflowExecutor.ExecutionResult execution = executor.execute(
                workflow, run.getId(), traceId == null ? run.getTraceId() : traceId, input,
                durableRunner, new WorkflowExecutor.CancellationToken(), completed);
        run.setStatus(execution.status().name());
        run.setErrorCode(execution.errorCode());
        run.setCompletedAt(Instant.now());
        persistConflicts(run, execution);
        runs.save(run);
        return new WorkflowRunOutcome(execution.status().name(), run.getId(), execution, execution.errorCode());
    }

    private WorkflowNodeResult executeNode(WorkflowRunEntity run, WorkflowNodeDefinition node,
                                           WorkflowExecutionContext context, WorkflowNodeRunner delegate) throws Exception {
        String key = idempotencyKey(run.getId(), node.code(), context.input());
        String payloadHash = payloadHash(run, node, context);
        String nodeOwner = ownerId + ":" + node.code();
        WorkflowRunStore.NodeAttemptCommand attempt = new WorkflowRunStore.NodeAttemptCommand(
                run.getId(), node.code(), key, payloadHash, run.getMemorySnapshotVersion() == null ? 0L : run.getMemorySnapshotVersion(), nodeOwner);
        WorkflowRunStore.StartAttemptResult started = acquireLease(attempt);
        if (started.alreadyAccepted()) {
            NodeExecutionResult accepted = attemptStore.findAccepted(attempt)
                    .orElseThrow(() -> new IllegalStateException("accepted node result missing"));
            return new WorkflowNodeResult(node.code(), accepted.status(), writeOutput(accepted.structuredOutput()),
                    accepted.evidenceIds(), 0L);
        }
        if (!started.leaseAcquired()) throw new IllegalStateException("WORKFLOW_NODE_LEASE_TIMEOUT: " + node.code());
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "workflow-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleAtFixedRate(() -> {
            try { attemptStore.renewLease(attempt); } catch (RuntimeException ignored) { /* completion remains fenced */ }
        }, LEASE_HEARTBEAT_MILLIS, LEASE_HEARTBEAT_MILLIS, TimeUnit.MILLISECONDS);
        try {
            WorkflowNodeResult result = delegate.run(node, context);
            Map<String, Object> output = parseOutput(result.structuredOutputJson());
            attemptStore.completeAttempt(new WorkflowRunStore.CompleteAttemptCommand(
                    run.getId(), node.code(), key, payloadHash, result.status(), output, result.evidenceIds(),
                    run.getMemorySnapshotVersion() == null ? 0L : run.getMemorySnapshotVersion(),
                    text(output, "roleCode"), text(output, "agentCode"), integer(output, "agentVersion"),
                    run.getId() + ":" + node.code(), Map.of("dependencyCount", context.completed().size()),
                    map(output, "metadata"), nodeOwner));
            return result;
        } finally {
            heartbeat.shutdownNow();
        }
    }

    private WorkflowRunStore.StartAttemptResult acquireLease(WorkflowRunStore.NodeAttemptCommand attempt) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LEASE_WAIT_MILLIS);
        WorkflowRunStore.StartAttemptResult started = attemptStore.startAttempt(attempt);
        while (!started.alreadyAccepted() && !started.leaseAcquired() && System.nanoTime() < deadline) {
            try { Thread.sleep(100L); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("WORKFLOW_NODE_LEASE_INTERRUPTED", interrupted); }
            started = attemptStore.startAttempt(attempt);
        }
        return started;
    }

    private void validatePersistedBinding(WorkflowRunEntity run, WorkflowDefinition workflow, WorkflowNodeRunner runner) {
        if (run.getManifestJson() == null || run.getManifestJson().isBlank()
                || run.getWorkflowDefinitionJson() == null || run.getWorkflowDefinitionJson().isBlank()) {
            throw new IllegalStateException("WORKFLOW_MANIFEST_NOT_FOUND");
        }
        try {
            WorkflowExecutionManifest persisted = objectMapper.readValue(run.getManifestJson(), WorkflowExecutionManifest.class);
            if (!run.getId().equals(persisted.runId()) || !workflow.code().equals(persisted.workflowCode())
                    || (run.getWorkflowVersion() != null && run.getWorkflowVersion() != persisted.workflowVersion())) {
                throw new IllegalStateException("WORKFLOW_MANIFEST_MISMATCH");
            }
            String supplied = runner.bindRunId(run.getId()).frozenManifestJson();
            if (supplied == null || !objectMapper.readTree(run.getManifestJson()).equals(objectMapper.readTree(supplied))) {
                throw new IllegalStateException("WORKFLOW_MANIFEST_MISMATCH");
            }
            Map<String, Object> definition = objectMapper.readValue(run.getWorkflowDefinitionJson(), OBJECT_MAP);
            if (!workflow.code().equals(String.valueOf(definition.get("code")))) {
                throw new IllegalStateException("WORKFLOW_DEFINITION_MISMATCH");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("WORKFLOW_MANIFEST_INVALID", exception);
        }
    }

    private static String text(Map<String, Object> output, String key) {
        Object value = output.get(key); return value == null ? null : String.valueOf(value);
    }
    private static Integer integer(Map<String, Object> output, String key) {
        Object value = output.get(key); if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(String.valueOf(value)); } catch (Exception ignored) { return null; }
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> output, String key) {
        Object value = output.get(key); return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Map<String, WorkflowNodeResult> loadCompleted(String runId) {
        Map<String, WorkflowNodeResult> result = new LinkedHashMap<>();
        List<WorkflowNodeRunEntity> rows = nodes.findByRunId(runId);
        if (rows == null) return result;
        for (WorkflowNodeRunEntity row : rows) {
            if (!"SUCCEEDED".equals(row.getStatus()) || row.getStructuredOutputJson() == null) continue;
            result.put(row.getNodeCode(), new WorkflowNodeResult(row.getNodeCode(), row.getStatus(),
                    row.getStructuredOutputJson(), split(row.getEvidenceIdsJson()), row.getLatencyMs()));
        }
        return result;
    }

    private boolean hasNonSuccessfulTerminal(String runId) {
        List<WorkflowNodeRunEntity> rows = nodes.findByRunId(runId);
        if (rows == null) return false;
        return rows.stream().anyMatch(row -> isTerminal(row.getStatus()) && !"SUCCEEDED".equals(row.getStatus()));
    }

    private void persistConflicts(WorkflowRunEntity run, WorkflowExecutor.ExecutionResult execution) {
        if (run.getTenantId() == null || run.getUserId() == null || run.getCaseId() == null
                || run.getMemorySnapshotVersion() == null || execution == null) return;
        CaseMemorySnapshot snapshot = new CaseMemorySnapshot(
                new CaseScope(run.getTenantId(), run.getUserId(), run.getCaseId()),
                run.getMemorySnapshotVersion(), Instant.now());
        List<NodeExecutionResult> results = execution.nodes().values().stream()
                .map(node -> new NodeExecutionResult(node.nodeCode(), node.nodeCode(), node.status(),
                        parseOutput(node.structuredOutputJson()), node.evidenceIds(), snapshot.version()))
                .toList();
        WorkflowReduction reduction = reducer.reduce(results, snapshot);
        for (WorkflowConflict conflict : reduction.conflicts()) {
            com.raglaw.agentscope.domain.WorkflowConflictEntity row = new com.raglaw.agentscope.domain.WorkflowConflictEntity();
            row.setId(UUID.randomUUID().toString());
            row.setRunId(run.getId());
            row.setSlot(conflict.slot());
            row.setConflictType(conflict.requiresApproval() ? "REQUIRES_APPROVAL" : "CONFLICT");
            row.setStatus("OPEN");
            row.setNodeResultIdsJson(writeOutput(Map.of("resultIds", conflict.resultIds(), "values", conflict.values())));
            row.setCreatedAt(Instant.now());
            conflicts.save(row);
        }
    }

    private Map<String, Object> parseOutput(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, OBJECT_MAP); }
        catch (Exception ignored) { return Map.of("raw", value); }
    }

    private String writeOutput(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception e) { throw new IllegalStateException("accepted workflow output is not serializable", e); }
    }

    private static String idempotencyKey(String runId, String nodeCode, String input) {
        return digest(runId + "|" + nodeCode + "|" + (input == null ? "" : input));
    }

    private static String payloadHash(WorkflowRunEntity run, WorkflowNodeDefinition node, WorkflowExecutionContext context) {
        return digest(run.getId() + "|" + node.code() + "|" + node.requiredRole() + "|" + context.input()
                + "|" + context.completed().keySet() + "|workflow=" + run.getWorkflowCode()
                + "|workflowVersion=" + run.getWorkflowVersion() + "|manifest=" + run.getManifestJson()
                + "|memory=" + run.getMemorySnapshotVersion());
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException("workflow key hashing failed", e); }
    }

    private static List<String> split(String values) {
        if (values == null || values.isBlank()) return List.of();
        return java.util.Arrays.stream(values.split(",")).filter(value -> !value.isBlank()).toList();
    }

    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)
                || "TIMED_OUT".equals(status) || "RETRY_EXHAUSTED".equals(status);
    }

    private static void require(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name);
    }
}
