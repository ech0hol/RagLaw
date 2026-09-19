package com.raglaw.agentscope.workflow;

import com.raglaw.agentscope.routing.TaskType;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowExecutorTest {
    private static WorkflowDefinition dag() {
        return new WorkflowDefinition("FICTIONAL", Set.of(TaskType.DISPUTE_ANALYSIS), Set.of(), List.of(
                new WorkflowNodeDefinition("FACTS", "FACT", List.of(), false),
                new WorkflowNodeDefinition("STATUTE", "STATUTE", List.of("FACTS"), true),
                new WorkflowNodeDefinition("CASE", "CASE", List.of("FACTS"), true),
                new WorkflowNodeDefinition("EVIDENCE", "EVIDENCE", List.of("STATUTE", "CASE"), false),
                new WorkflowNodeDefinition("SYNTHESIS", "SYNTHESIS", List.of("EVIDENCE"), false)));
    }
    private static WorkflowNodeResult result(String code) { return new WorkflowNodeResult(code, "SUCCEEDED", "{\"fictional\":true}", List.of("evidence-" + code), 1); }

    @Test void schedulesDependenciesAndParallelBranches() {
        try (WorkflowExecutor executor = new WorkflowExecutor(4, Duration.ofSeconds(2), 1)) {
            List<String> order = new CopyOnWriteArrayList<>(); CountDownLatch branches = new CountDownLatch(2);
            var out = executor.execute(dag(), "run-fictional", "trace-fictional", "fictional facts", (n, c) -> {
                order.add(n.code()); if (n.code().equals("STATUTE") || n.code().equals("CASE")) { branches.countDown(); assertTrue(c.completed().containsKey("FACTS")); }
                return result(n.code());
            });
            assertEquals(WorkflowExecutor.Status.SUCCEEDED, out.status()); assertTrue(branches.getCount() == 0);
            assertTrue(order.indexOf("FACTS") < order.indexOf("STATUTE")); assertTrue(order.indexOf("FACTS") < order.indexOf("CASE"));
            assertTrue(order.indexOf("EVIDENCE") > order.indexOf("STATUTE") && order.indexOf("EVIDENCE") > order.indexOf("CASE"));
            assertEquals(5, out.nodes().size());
        }
    }
    @Test void failureAndRetryExhaustionAreTerminal() {
        try (WorkflowExecutor executor = new WorkflowExecutor(2, Duration.ofSeconds(2), 2)) {
            var out = executor.execute(dag(), "r", "t", "x", (n, c) -> { if (n.code().equals("FACTS")) throw new IllegalStateException("fictional-failure"); return result(n.code()); });
            assertEquals(WorkflowExecutor.Status.RETRY_EXHAUSTED, out.status());
        }
    }
    @Test void timeoutAndCancellationAreTerminal() throws Exception {
        try (WorkflowExecutor executor = new WorkflowExecutor(1, Duration.ofMillis(50), 1)) {
            var timeout = executor.execute(dag(), "r", "t", "x", (n, c) -> { Thread.sleep(200); return result(n.code()); });
            assertEquals(WorkflowExecutor.Status.TIMED_OUT, timeout.status());
            var token = new WorkflowExecutor.CancellationToken(); token.cancel();
            var cancelled = executor.execute(dag(), "r", "t", "x", (n, c) -> result(n.code()), token);
            assertEquals(WorkflowExecutor.Status.CANCELLED, cancelled.status());
        }
    }

    @Test void inFlightCancellationIsObservedAndInterruptsWait() throws Exception {
        try (WorkflowExecutor executor = new WorkflowExecutor(1, Duration.ofSeconds(5), 1)) {
            var caller = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
            var token = new WorkflowExecutor.CancellationToken();
            var started = new CountDownLatch(1);
            var definition = new WorkflowDefinition("CANCEL", Set.of(TaskType.DISPUTE_ANALYSIS), Set.of(),
                    List.of(new WorkflowNodeDefinition("FACTS", "FACT", List.of(), false)));
            var future = caller.submit(() -> executor.execute(definition, "r", "t", "x", (n, context) -> {
                started.countDown();
                while (!context.cancelled()) {
                    try { Thread.sleep(10); } catch (InterruptedException ignored) { /* token is authoritative */ }
                }
                return new WorkflowNodeResult(n.code(), "CANCELLED", "{}", List.of(), 0);
            }, token));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            token.cancel();
                assertEquals(WorkflowExecutor.Status.CANCELLED, future.get(1, TimeUnit.SECONDS).status());
            } finally {
                caller.shutdownNow();
            }
        }
    }
}
