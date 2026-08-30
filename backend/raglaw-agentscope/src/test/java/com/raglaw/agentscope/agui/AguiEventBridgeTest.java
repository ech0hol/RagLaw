package com.raglaw.agentscope.agui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raglaw.agentscope.runtime.AgentRunSession;
import com.raglaw.agentscope.tools.AgentscopeRagSearchTool;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class AguiEventBridgeTest {

    @Test
    void countCitableKnowledgeHitsExcludesCatalogSummary() {
        List<RagSearchHit> hits = List.of(
                new RagSearchHit(
                        "catalog-summary", null, 1.0, "/KNOWLEDGE", "概览", "概览", "概览", "catalog-summary"),
                new RagSearchHit("chunk-1", "doc-1", 0.9, "/STATUTE", "条文", "条文", "民法典", null),
                new RagSearchHit("chunk-2", "doc-2", 0.8, "/STATUTE", "条文2", "条文2", "刑法", null)
        );
        AgentRunSession session = new AgentRunSession("task-1");
        session.recordSearch(
                new com.raglaw.rag.tool.RagSearchResult(hits, null, java.util.Map.of()),
                10L
        );
        int baseIndex = session.webReferences().size()
                + ReferencePayloadBuilder.countCitableKnowledgeHits(session.hits());
        assertEquals(2, baseIndex);
    }

    @Test
    void shouldResetTextOnRagSearchAndTavily() {
        assertTrue(AguiEventBridge.shouldResetTextOnToolCall(AgentscopeRagSearchTool.TOOL_NAME));
        assertTrue(AguiEventBridge.shouldResetTextOnToolCall("tavily-search"));
        assertFalse(AguiEventBridge.shouldResetTextOnToolCall("other-tool"));
    }

    @Test
    void streamStateResetTextClearsBuffer() {
        AguiEventBridge.AguiStreamState state = new AguiEventBridge.AguiStreamState();
        state.append("草稿回答");
        assertEquals(4, state.length());
        state.resetText();
        assertEquals(0, state.length());
        assertEquals("", state.text());
    }
}
