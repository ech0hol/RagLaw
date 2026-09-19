package com.raglaw.server.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.TaskRouteDecisionEntity;
import com.raglaw.agentscope.domain.TaskRouteDecisionRepository;
import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.expert.ExpertRouter;
import com.raglaw.agentscope.routing.TaskRouteObserver;
import com.raglaw.agentscope.routing.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused, fictional integration-style contract for shadow isolation. */
class TaskRouteShadowIT {
    @Test
    void changedCandidateIsPersistedWithoutReplacingExpertRouterPath() throws Exception {
        ExpertRouter router = mock(ExpertRouter.class);
        ExpertContext actual = new ExpertContext("STATUTE", "Fictional Statute Expert", "prompt", List.of(), "STATUTE", "direct", false, false, null, false, List.of(), List.of());
        when(router.resolve(any(), any(), any())).thenReturn(actual);
        ExpertContext pathUsedByExecution = router.resolve(null, "fictional statute question", null);

        TaskRouteDecisionRepository repository = mock(TaskRouteDecisionRepository.class);
        TaskRouteObserver observer = new TaskRouteObserver(repository, new ObjectMapper(), 1, 8);
        observer.observeAsync("trace-fictional", pathUsedByExecution,
                new RouteDecision(TaskType.CONTRACT_REVIEW, RiskLevel.MEDIUM, ExecutionMode.HUMAN_REVIEW,
                        "fictional-contract-role", "CONTRACT_REVIEW", List.of("fictional-change"), true, "policy-fictional"),
                List.of("fictional-contract"), "prompt-fictional", "model-fictional", 11L);
        verify(repository, timeout(1000)).save(any(TaskRouteDecisionEntity.class));
        assertThat(pathUsedByExecution).isSameAs(actual);
        verify(router).resolve(any(), any(), any());
        observer.shutdown();
    }
}
