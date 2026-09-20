package com.raglaw.agentscope.workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/** Bounded, cancellable DAG executor. Only ready nodes are submitted and terminal state is deterministic. */
@org.springframework.stereotype.Component
public class WorkflowExecutor implements AutoCloseable {
    public enum Status { SUCCEEDED, FAILED, CANCELLED, TIMED_OUT, RETRY_EXHAUSTED }
    public record ExecutionResult(Status status, Map<String, WorkflowNodeResult> nodes, String errorCode) { }
    private final ExecutorService executor;
    private final long timeoutMs;
    private final int maxAttempts;

    public WorkflowExecutor() { this(4, Duration.ofSeconds(30), 2); }
    public WorkflowExecutor(int workers, Duration timeout, int maxAttempts) {
        if (workers < 1 || maxAttempts < 1) throw new IllegalArgumentException("workers and attempts must be positive");
        this.executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, workers * 4)), new ThreadPoolExecutor.CallerRunsPolicy());
        this.timeoutMs = Math.max(1, timeout.toMillis()); this.maxAttempts = maxAttempts;
    }
    public ExecutionResult execute(WorkflowDefinition definition, String runId, String traceId, String input,
                                   WorkflowNodeRunner runner) {
        return execute(definition, runId, traceId, input, runner, new CancellationToken());
    }
    public ExecutionResult execute(WorkflowDefinition definition, String runId, String traceId, String input,
                                   WorkflowNodeRunner runner, CancellationToken cancellation) {
        return execute(definition, runId, traceId, input, runner, cancellation, Map.of());
    }
    public ExecutionResult execute(WorkflowDefinition definition, String runId, String traceId, String input,
                                   WorkflowNodeRunner runner, CancellationToken cancellation,
                                   Map<String, WorkflowNodeResult> initialCompleted) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        Map<String, WorkflowNodeResult> done = new LinkedHashMap<>();
        if (initialCompleted != null) done.putAll(initialCompleted);
        Set<String> remaining = new java.util.LinkedHashSet<>(); definition.nodes().forEach(n -> remaining.add(n.code()));
        remaining.removeAll(done.keySet());
        while (!remaining.isEmpty()) {
                if (cancellation.cancelled()) return new ExecutionResult(Status.CANCELLED, done, "CANCELLED");
                if (System.nanoTime() >= deadline) return new ExecutionResult(Status.TIMED_OUT, done, "WORKFLOW_TIMEOUT");
                List<WorkflowNodeDefinition> ready = definition.nodes().stream().filter(n -> remaining.contains(n.code())
                        && done.keySet().containsAll(n.dependsOn())).toList();
                if (ready.isEmpty()) return new ExecutionResult(Status.FAILED, done, "NO_READY_NODE");
                Map<WorkflowNodeDefinition, Future<WorkflowNodeResult>> futures = new LinkedHashMap<>();
                Map<String, WorkflowNodeResult> snapshot = Map.copyOf(done);
                for (WorkflowNodeDefinition n : ready) futures.put(n, executor.submit(() -> runWithRetry(n, runner,
                        new WorkflowExecutionContext(runId, traceId, input, snapshot, cancellation), cancellation, deadline)));
                for (var entry : futures.entrySet()) {
                    try {
                        WorkflowNodeResult result;
                        while (true) {
                            if (cancellation.cancelled()) {
                                futures.values().forEach(f -> f.cancel(true));
                                return new ExecutionResult(Status.CANCELLED, done, "CANCELLED");
                            }
                            long left = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
                            try {
                                result = entry.getValue().get(Math.min(left, 50), TimeUnit.MILLISECONDS);
                                break;
                            } catch (TimeoutException poll) {
                                if (System.nanoTime() >= deadline) throw poll;
                            }
                        }
                        if ("CANCELLED".equals(result.status())) { cancellation.cancel(); futures.values().forEach(f -> f.cancel(true)); return new ExecutionResult(Status.CANCELLED, done, "CANCELLED"); }
                        done.put(entry.getKey().code(), result); remaining.remove(entry.getKey().code());
                    }
                    catch (CancellationException e) { return new ExecutionResult(Status.CANCELLED, done, "CANCELLED"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); futures.values().forEach(f -> f.cancel(true)); return new ExecutionResult(Status.CANCELLED, done, "INTERRUPTED"); }
                    catch (TimeoutException e) { futures.values().forEach(f -> f.cancel(true)); return new ExecutionResult(Status.TIMED_OUT, done, "NODE_TIMEOUT"); }
                    catch (ExecutionException e) { futures.values().forEach(f -> f.cancel(true)); return new ExecutionResult(e.getCause() instanceof RetryExhausted ? Status.RETRY_EXHAUSTED : Status.FAILED, done, code(e.getCause())); }
                }
        }
        return new ExecutionResult(Status.SUCCEEDED, done, null);
    }
    private WorkflowNodeResult runWithRetry(WorkflowNodeDefinition node, WorkflowNodeRunner runner, WorkflowExecutionContext context,
                                             CancellationToken token, long deadline) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (token.cancelled()) throw new CancellationException();
            try { return runner.run(node, context); }
            catch (CancellationException e) { throw e; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancellationException(); }
            catch (Exception e) { last = e; if (token.cancelled()) throw new CancellationException(); if (System.nanoTime() >= deadline) throw new TimeoutException(); }
        }
        throw new RetryExhausted(last);
    }
    private static String code(Throwable t) { return t == null ? "WORKFLOW_FAILURE" : (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()); }
    public static final class CancellationToken { private volatile boolean cancelled; public void cancel() { cancelled = true; } public boolean cancelled() { return cancelled; } }
    private static final class RetryExhausted extends Exception { RetryExhausted(Throwable cause) { super(cause); } }
    @Override public void close() { executor.shutdownNow(); }
}
