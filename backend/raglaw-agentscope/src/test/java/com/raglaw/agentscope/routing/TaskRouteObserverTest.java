package com.raglaw.agentscope.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.TaskRouteDecisionEntity;
import com.raglaw.agentscope.domain.TaskRouteDecisionRepository;
import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.trace.TraceRecorder;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TaskRouteObserverTest {
    @Test
    void persistsShadowDecisionAndJsonFields() throws Exception {
        TaskRouteDecisionRepository repository = mock(TaskRouteDecisionRepository.class);
        TaskRouteObserver observer = new TaskRouteObserver(repository, new ObjectMapper(), 1, 8);
        ExpertContext actual = new ExpertContext("STATUTE", "Statute Expert", "prompt", List.of(), "STATUTE", "direct", false, false, null, false, List.of(), List.of());
        RouteDecision candidate = new RouteDecision(TaskType.CONTRACT_REVIEW, RiskLevel.MEDIUM, ExecutionMode.HUMAN_REVIEW, "contract-role", "CONTRACT_REVIEW", List.of("fictional reason"), true, "policy-7");
        observer.observeAsync("trace-fictional", actual, candidate, List.of("contract"), "prompt-2", "model-2", 17L);
        observer.shutdown();
        verify(repository, timeout(1000)).save(argThat(row -> row.getTraceId().equals("trace-fictional") && row.getActualExpertCode().equals("STATUTE") && row.getCandidateTaskType().equals("CONTRACT_REVIEW") && row.getAgreement() == false && row.getClassifierLatencyMs() == 17L && row.getPolicyReasonsJson().contains("fictional reason") && row.getMissingMaterialsJson().contains("contract")));
    }

    @Test
    void repositoryFailureIsContained() throws Exception {
        TaskRouteDecisionRepository repository = mock(TaskRouteDecisionRepository.class);
        doThrow(new IllegalStateException("db")).when(repository).save(any());
        TaskRouteObserver observer = new TaskRouteObserver(repository, new ObjectMapper(), 1, 8);
        observer.observeAsync("trace-fictional", new ExpertContext("A", "A", "", List.of(), "A", "", false, false, null, false, List.of(), List.of()), new RouteDecision(TaskType.STATUTE_LOOKUP, RiskLevel.LOW, ExecutionMode.SINGLE_AGENT, "r", null, List.of(), false, "p"), List.of(), "pv", "mv", 1L);
        observer.shutdown();
        assertThat(observer.persistenceFailureCount()).isEqualTo(1);
    }

    @Test
    void queueRejectionIsCounted() throws Exception {
        TaskRouteDecisionRepository repository = mock(TaskRouteDecisionRepository.class);
        TaskRouteObserver observer = new TaskRouteObserver(repository, new ObjectMapper(), 1, 1);
        observer.blockWorkers();
        for (int i = 0; i < 4; i++) observer.observeAsync("t", new ExpertContext("A", "A", "", List.of(), "A", "", false, false, null, false, List.of(), List.of()), new RouteDecision(TaskType.STATUTE_LOOKUP, RiskLevel.LOW, ExecutionMode.SINGLE_AGENT, "r", null, List.of(), false, "p"), List.of(), "pv", "mv", 1L);
        assertThat(observer.rejectionCount()).isPositive();
        observer.shutdown();
    }

    @Test
    void recordsSafeTraceStageWithoutAffectingObservation() throws Exception {
        TaskRouteDecisionRepository repository = mock(TaskRouteDecisionRepository.class);
        TraceRecorder traceRecorder = mock(TraceRecorder.class);
        TaskRouteObserver observer = new TaskRouteObserver(repository, new ObjectMapper(), 1, 8);
        observer.setTraceRecorder(traceRecorder);
        observer.observeAsync("trace-fictional", new ExpertContext("STATUTE", "A", "", List.of(), "A", "", false, false, null, false, List.of(), List.of()), new RouteDecision(TaskType.CONTRACT_REVIEW, RiskLevel.MEDIUM, ExecutionMode.HUMAN_REVIEW, "r", null, List.of(), false, "p"), List.of(), "pv", "mv", 1L);
        verify(traceRecorder, timeout(1000)).recordStage(eq("trace-fictional"), eq("task_route_shadow"), any(), eq(0L));
        observer.shutdown();
    }
}
