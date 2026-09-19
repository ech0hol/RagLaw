package com.raglaw.agentscope.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.TaskRouteDecisionEntity;
import com.raglaw.agentscope.domain.TaskRouteDecisionRepository;
import com.raglaw.agentscope.expert.ExpertContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskRouteObserver {
    private static final Logger log = LoggerFactory.getLogger(TaskRouteObserver.class);
    private final TaskRouteDecisionRepository repository;
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor executor;
    private final AtomicLong rejections = new AtomicLong();
    private final AtomicLong persistenceFailures = new AtomicLong();
    private volatile CountDownLatch workerGate;

    public TaskRouteObserver(TaskRouteDecisionRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, 2, 200);
    }
    public TaskRouteObserver(TaskRouteDecisionRepository repository, ObjectMapper objectMapper, int workers, int queueCapacity) {
        this.repository = repository; this.objectMapper = objectMapper;
        this.executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), new ThreadPoolExecutor.AbortPolicy());
    }

    public void observeAsync(String traceId, ExpertContext actual, RouteDecision candidate,
                             List<String> missingMaterials, String promptVersion,
                             String modelVersion, long classifierLatencyMs) {
        if (candidate == null || actual == null) return;
        try { executor.execute(() -> persist(traceId, actual, candidate, missingMaterials, promptVersion, modelVersion, classifierLatencyMs)); }
        catch (RejectedExecutionException e) { rejections.incrementAndGet(); log.warn("Task route shadow queue full traceId={}", traceId); }
    }
    public long rejectionCount() { return rejections.get(); }
    public long persistenceFailureCount() { return persistenceFailures.get(); }
    /** Compatibility hook for the current facade boundary where classifier provenance is unavailable. */
    public void observeAsync(String traceId, ExpertContext actual, String query) {
        String text = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        TaskType type = text.contains("contract") || text.contains("合同") ? TaskType.CONTRACT_REVIEW : TaskType.STATUTE_LOOKUP;
        RouteDecision candidate = new RouteDecision(type, RiskLevel.LOW, ExecutionMode.SINGLE_AGENT,
                "unknown-role", null, List.of("facade-shadow-placeholder"), false, "unknown-policy");
        observeAsync(traceId, actual, candidate, List.of(), "unknown-prompt", "unknown-model", 0L);
    }
    public void shutdown() { executor.shutdownNow(); }
    public void blockWorkers() { workerGate = new CountDownLatch(1); executor.prestartAllCoreThreads(); }
    private void persist(String traceId, ExpertContext actual, RouteDecision c, List<String> missing, String prompt, String model, long latency) {
        try {
            CountDownLatch gate = workerGate;
            if (gate != null) gate.await(5, TimeUnit.SECONDS);
            TaskRouteDecisionEntity e = new TaskRouteDecisionEntity();
            e.setId(UUID.randomUUID().toString()); e.setTraceId(traceId); e.setCreatedAt(Instant.now());
            e.setActualExpertCode(actual.peerCode()); e.setActualExpertName(actual.peerName());
            e.setCandidateTaskType(c.taskType().name()); e.setRiskLevel(c.riskLevel().name()); e.setExecutionMode(c.executionMode().name());
            e.setExpertRole(c.expertRole()); e.setWorkflowCode(c.workflowCode());
            e.setAgreement(agrees(actual.peerCode(), c.taskType()));
            e.setClassifierLatencyMs(latency); e.setPromptVersion(prompt); e.setModelVersion(model); e.setPolicyVersion(c.policyVersion());
            e.setPolicyReasonsJson(objectMapper.writeValueAsString(c.policyReasons())); e.setMissingMaterialsJson(objectMapper.writeValueAsString(missing == null ? List.of() : missing));
            repository.save(e);
        } catch (Exception ex) { persistenceFailures.incrementAndGet(); log.warn("Task route shadow persistence failed traceId={}", traceId, ex); }
    }

    private static boolean agrees(String expertCode, TaskType taskType) {
        if (expertCode == null || taskType == null) return false;
        String code = expertCode.replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        String task = taskType.name();
        return code.equals(task) || task.startsWith(code + "_") || code.startsWith(task + "_");
    }
}
