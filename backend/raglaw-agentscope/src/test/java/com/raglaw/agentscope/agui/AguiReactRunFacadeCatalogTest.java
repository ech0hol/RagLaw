package com.raglaw.agentscope.agui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.agentscope.a2a.QuestionRecommender;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.expert.ExpertRouter;
import com.raglaw.agentscope.runtime.AgentRunFactory;
import com.raglaw.agentscope.runtime.AgentRunSession;
import com.raglaw.agentscope.shadow.ShadowRouteObserver;
import com.raglaw.agentscope.tools.AgentscopeRagSearchTool;
import com.raglaw.agentscope.trace.TraceContext;
import com.raglaw.agentscope.trace.TraceRecorder;
import com.raglaw.chat.service.ConversationService;
import com.raglaw.rag.contract.ContractChatContextBuilder;
import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchResult;
import com.raglaw.rag.tool.RagSearchTool;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class AguiReactRunFacadeCatalogTest {

    private static final String CATALOG_QUERY = "当前有哪些法规可以查询";

    @Mock
    private ExpertRouter expertRouter;
    @Mock
    private AgentRunFactory agentRunFactory;
    @Mock
    private AguiEventBridge eventBridge;
    @Mock
    private TraceRecorder traceRecorder;
    @Mock
    private ConversationService conversationService;
    @Mock
    private RagSearchTool ragSearchTool;
    @Mock
    private ReferencePayloadBuilder referencePayloadBuilder;
    @Mock
    private QuestionRecommender questionRecommender;
    @Mock
    private Environment environment;
    @Mock
    private ShadowRouteObserver shadowRouteObserver;
    @Mock
    private ContractChatContextBuilder contractChatContextBuilder;

    private AguiReactRunFacade facade;

    @BeforeEach
    void setUp() {
        AgentscopeLlmProperties llmProperties = new AgentscopeLlmProperties();
        llmProperties.setMock(true);
        facade = new AguiReactRunFacade(
                expertRouter,
                agentRunFactory,
                eventBridge,
                traceRecorder,
                conversationService,
                new TaskCancellationRegistry(),
                ragSearchTool,
                referencePayloadBuilder,
                questionRecommender,
                llmProperties,
                environment,
                shadowRouteObserver,
                contractChatContextBuilder
        );
    }

    @Test
    void maybePresearchCatalogRecordsHitsAndEmitsReferences() throws Exception {
        RagSearchHit summary = new RagSearchHit(
                "catalog-summary",
                null,
                1.0,
                "/KNOWLEDGE",
                "概览",
                "概览",
                "知识库概览",
                "catalog-summary"
        );
        RagSearchHit doc = new RagSearchHit(
                "catalog-d1",
                "d1",
                1.0,
                "/STATUTE/CIVIL",
                "类型: 法规",
                "类型: 法规",
                "民法典",
                "catalog-d1"
        );
        RagSearchResult catalogResult = new RagSearchResult(
                List.of(summary, doc),
                null,
                Map.of("catalogMode", true, "catalogHitCount", 2)
        );
        when(ragSearchTool.searchDetailed(
                eq(CATALOG_QUERY),
                any(),
                eq(30),
                anyString(),
                any(),
                anyBoolean(),
                anyString()
        )).thenReturn(catalogResult);

        ExpertContext expert = delegatedStatuteExpert();
        AgentRunSession session = new AgentRunSession("task-1");
        TraceContext trace = TraceContext.create();
        SseEmitter emitter = new SseEmitter();

        invokeMaybePresearchCatalog(emitter, session, expert, CATALOG_QUERY, trace);

        assertThat(session.hits()).hasSize(2);
        verify(traceRecorder).recordStage(
                eq(trace.traceId()),
                eq("catalog_presearch"),
                any(),
                anyLong()
        );
        verify(eventBridge).emitKnowledgeReferences(emitter, session, CATALOG_QUERY);
    }

    @Test
    void maybePresearchCatalogSkipsNonCatalogQuery() throws Exception {
        ExpertContext expert = delegatedStatuteExpert();
        AgentRunSession session = new AgentRunSession("task-1");
        TraceContext trace = TraceContext.create();
        SseEmitter emitter = new SseEmitter();

        invokeMaybePresearchCatalog(emitter, session, expert, "劳动合同解除条件？", trace);

        assertThat(session.hits()).isEmpty();
        verify(ragSearchTool, never()).searchDetailed(any(), any(), anyInt(), any(), any(), anyBoolean(), any());
        verify(eventBridge, never()).emitKnowledgeReferences(any(), any(), anyString());
    }

    @Test
    void maybePresearchCatalogSkipsWhenExpertHasNoRagSearch() throws Exception {
        ExpertContext expert = new ExpertContext(
                "GENERAL",
                "通用助手",
                "prompt",
                List.of(),
                "GENERAL",
                "direct",
                false,
                false,
                null,
                false,
                List.of("tavily-search"),
                List.of()
        );
        AgentRunSession session = new AgentRunSession("task-1");
        TraceContext trace = TraceContext.create();
        SseEmitter emitter = new SseEmitter();

        invokeMaybePresearchCatalog(emitter, session, expert, CATALOG_QUERY, trace);

        assertThat(session.hits()).isEmpty();
        verify(ragSearchTool, never()).searchDetailed(any(), any(), anyInt(), any(), any(), anyBoolean(), any());
    }

    private static ExpertContext delegatedStatuteExpert() {
        return new ExpertContext(
                "STATUTE",
                "法规助手",
                "prompt",
                List.of("STATUTE_CIVIL"),
                "STATUTE",
                "rule:STATUTE",
                false,
                false,
                null,
                true,
                List.of(AgentscopeRagSearchTool.TOOL_NAME, "tavily-search"),
                List.of()
        );
    }

    private void invokeMaybePresearchCatalog(
            SseEmitter emitter,
            AgentRunSession session,
            ExpertContext expert,
            String userMessage,
            TraceContext trace
    ) throws Exception {
        Method method = AguiReactRunFacade.class.getDeclaredMethod(
                "maybePresearchCatalog",
                SseEmitter.class,
                AgentRunSession.class,
                ExpertContext.class,
                String.class,
                TraceContext.class
        );
        method.setAccessible(true);
        method.invoke(facade, emitter, session, expert, userMessage, trace);
    }
}
