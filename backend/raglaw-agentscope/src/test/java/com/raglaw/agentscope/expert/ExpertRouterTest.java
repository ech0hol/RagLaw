package com.raglaw.agentscope.expert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentadmin.registry.AgentRegistry;
import com.raglaw.agentscope.a2a.A2aPeerSelector;
import com.raglaw.agentscope.a2a.PeerSelection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpertRouterTest {

    @Mock
    private A2aPeerSelector peerSelector;
    @Mock
    private AgentRegistry agentRegistry;

    private ExpertRouter router;

    @BeforeEach
    void setUp() {
        router = new ExpertRouter(peerSelector, agentRegistry);
    }

    @Test
    void directAgentSkipsDelegation() {
        AgentConfigSnapshot statute = statuteAgent();
        ExpertContext context = router.resolve(statute, "拖欠工资", null);

        assertEquals("STATUTE", context.peerCode());
        assertFalse(context.delegated());
        assertEquals("direct:STATUTE", context.routeReason());
    }

    @Test
    void generalDelegatesToStatuteForLaborQuestion() {
        AgentConfigSnapshot general = generalAgent();
        AgentConfigSnapshot statute = statuteAgent();
        when(peerSelector.select(A2aPeerSelector.MVP_A2A_PEERS, "公司拖欠工资如何维权"))
                .thenReturn(new PeerSelection("STATUTE", "rule:STATUTE", false));
        when(agentRegistry.get("STATUTE")).thenReturn(statute);

        ExpertContext context = router.resolve(general, "公司拖欠工资如何维权", null);

        assertTrue(context.delegated());
        assertEquals("STATUTE", context.peerCode());
        assertEquals("rule:STATUTE", context.routeReason());
        assertEquals(List.of("STATUTE_CIVIL"), context.knowledgeScopes());
        assertEquals(List.of("rag_search"), context.tools());
    }

    private static AgentConfigSnapshot generalAgent() {
        return new AgentConfigSnapshot(
                "GENERAL",
                "通用",
                "GENERAL",
                "dashscope:qwen-plus",
                "你是通用助手。",
                List.of(),
                List.of(),
                A2aPeerSelector.MVP_A2A_PEERS,
                List.of(),
                List.of()
        );
    }

    private static AgentConfigSnapshot statuteAgent() {
        return new AgentConfigSnapshot(
                "STATUTE",
                "法规助手",
                "STATUTE",
                "dashscope:qwen-plus",
                "你是法规专家。",
                List.of(),
                List.of("STATUTE_CIVIL"),
                List.of(),
                List.of("rag_search"),
                List.of()
        );
    }
}
