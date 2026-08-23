package com.raglaw.agentscope.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentadmin.registry.AgentRegistry;
import com.raglaw.agentscope.trace.TraceRecorder;
import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchTool;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class A2aOrchestratorTest {

    @Mock
    private AgentRegistry agentRegistry;
    @Mock
    private RagSearchTool ragSearchTool;
    @Mock
    private TraceRecorder traceRecorder;

    private A2aOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new A2aOrchestrator(agentRegistry, ragSearchTool, traceRecorder);
    }

    @Test
    void delegatesLaborQuestionToStatutePeer() throws Exception {
        AgentConfigSnapshot general = new AgentConfigSnapshot(
                "GENERAL", "通用", "GENERAL", "dashscope:qwen-plus", "sys", List.of(), List.of(),
                List.of("STATUTE_CIVIL", "CASE_CIVIL", "CONTRACT_GENERAL"), List.of()
        );
        AgentConfigSnapshot statute = new AgentConfigSnapshot(
                "STATUTE_CIVIL", "法规", "STATUTE", "dashscope:qwen-plus", "sys", List.of(),
                List.of("cat_l2_statute_civil"), List.of(), List.of("rag_search")
        );
        when(agentRegistry.get("STATUTE_CIVIL")).thenReturn(statute);
        when(ragSearchTool.search(anyString(), anyList(), anyInt(), anyString()))
                .thenReturn(List.of(new RagSearchHit("c1", "d1", 0.9, "/STATUTE", "excerpt")));

        A2aOrchestrator.A2aResult result = orchestrator.delegate(
                general,
                "公司拖欠工资如何维权",
                "trace-1",
                new SseEmitter()
        );

        assertEquals("STATUTE_CIVIL", result.peerCode());
        assertEquals(1, result.hits().size());
        verify(traceRecorder).recordA2aCall(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }
}
