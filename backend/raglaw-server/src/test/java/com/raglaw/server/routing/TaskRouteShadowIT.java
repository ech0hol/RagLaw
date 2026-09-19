package com.raglaw.server.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.agui.AguiEventBridge;
import com.raglaw.agentscope.agui.AguiReactRunFacade;
import com.raglaw.agentscope.agui.ReferencePayloadBuilder;
import com.raglaw.agentscope.a2a.QuestionRecommender;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import com.raglaw.agentscope.domain.TaskRouteDecisionEntity;
import com.raglaw.agentscope.domain.TaskRouteDecisionRepository;
import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.expert.ExpertRouter;
import com.raglaw.agentscope.routing.TaskRouteObserver;
import com.raglaw.agentscope.shadow.ShadowRouteObserver;
import com.raglaw.agentscope.runtime.AgentRunFactory;
import com.raglaw.agentscope.trace.TraceRecorder;
import com.raglaw.chat.service.ConversationService;
import com.raglaw.rag.contract.ContractChatContextBuilder;
import com.raglaw.rag.tool.RagSearchTool;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

/** Focused, fictional integration-style contract for shadow isolation. */
class TaskRouteShadowIT {
    @Test
    void facadeShadowSeamPersistsChangedCandidateWithoutReplacingExpertPath() throws Exception {
        ExpertRouter router = mock(ExpertRouter.class);
        ExpertContext actual = new ExpertContext(
                "STATUTE", "Fictional Statute Expert", "prompt", List.of("fictional-statute"),
                "STATUTE", "direct", false, false, null, false, List.of("search"), List.of());
        when(router.resolve(any(), any(), any())).thenReturn(actual);
        ExpertContext pathUsedByExecution = router.resolve(null, "fictional statute question", null);

        TaskRouteDecisionRepository repository = mock(TaskRouteDecisionRepository.class);
        TaskRouteObserver observer = new TaskRouteObserver(repository, new ObjectMapper(), 1, 8);
        AguiReactRunFacade facade = new AguiReactRunFacade(
                router, mock(AgentRunFactory.class), mock(AguiEventBridge.class), mock(TraceRecorder.class),
                mock(ConversationService.class), mock(com.raglaw.agentscope.agui.TaskCancellationRegistry.class),
                mock(RagSearchTool.class), mock(ReferencePayloadBuilder.class), mock(QuestionRecommender.class),
                mock(AgentscopeLlmProperties.class), mock(Environment.class), mock(ShadowRouteObserver.class),
                mock(ContractChatContextBuilder.class));
        Field observerField = AguiReactRunFacade.class.getDeclaredField("taskRouteObserver");
        observerField.setAccessible(true);
        observerField.set(facade, observer);

        try {
            facade.observeTaskRouteShadow("trace-fictional", pathUsedByExecution, "fictional contract question");
            var saved = org.mockito.ArgumentCaptor.forClass(TaskRouteDecisionEntity.class);
            verify(repository, timeout(1000).times(1)).save(saved.capture());
            assertThat(saved.getValue().getCandidateTaskType()).isEqualTo("CONTRACT_REVIEW");
            assertThat(saved.getValue().getTraceId()).isEqualTo("trace-fictional");
            assertThat(pathUsedByExecution).isSameAs(actual);
            assertThat(pathUsedByExecution.peerCode()).isEqualTo("STATUTE");
            assertThat(pathUsedByExecution.tools()).containsExactly("search");
            verify(router).resolve(any(), any(), any());
            assertMigrationContract();
        } finally {
            observer.shutdown();
        }
    }

    private static void assertMigrationContract() throws Exception {
        try (InputStream stream = TaskRouteShadowIT.class.getClassLoader()
                .getResourceAsStream("db/migration/V39__task_route_decision.sql")) {
            assertThat(stream).as("V39 migration classpath resource").isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("create table raglaw_task_route_decision");
            assertThat(sql).contains("idx_task_route_decision_trace");
            assertThat(sql).contains("idx_task_route_decision_created");
            assertThat(sql).contains("idx_task_route_decision_risk");
            assertThat(sql).contains("idx_task_route_decision_mode");
            assertThat(sql).contains("idx_task_route_decision_task_created");
        }
    }
}
