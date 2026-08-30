package com.raglaw.agentscope.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRunSessionTest {

    @Test
    void recordSearchPreservesCatalogHitsWhenSemanticSearchReturnsEmpty() {
        AgentRunSession session = new AgentRunSession("task-1");
        RagSearchHit catalogHit = new RagSearchHit(
                "catalog-d1",
                "d1",
                1.0,
                "/STATUTE/CIVIL",
                "类型: 法规",
                "类型: 法规",
                "民法典",
                "catalog-d1"
        );
        session.recordSearch(
                new RagSearchResult(
                        List.of(catalogHit),
                        null,
                        Map.of("catalogMode", true)
                ),
                10L
        );

        session.recordSearch(
                new RagSearchResult(
                        List.of(),
                        null,
                        Map.of("fulltextCount", 0, "rrfCount", 0)
                ),
                20L
        );

        assertEquals(1, session.hits().size());
        assertTrue(Boolean.TRUE.equals(session.lastRetrievalTrace().get("catalogMode")));
    }

    @Test
    void recordSearchClearsCatalogHitsWhenSemanticSearchReturnsResults() {
        AgentRunSession session = new AgentRunSession("task-1");
        RagSearchHit catalogHit = new RagSearchHit(
                "catalog-d1",
                "d1",
                1.0,
                "/STATUTE/CRIMINAL",
                "类型: 法规",
                "类型: 法规",
                "刑法",
                "catalog-d1"
        );
        session.recordSearch(
                new RagSearchResult(
                        List.of(catalogHit),
                        null,
                        Map.of("catalogMode", true)
                ),
                10L
        );

        RagSearchHit semanticHit = new RagSearchHit(
                "chunk-1",
                "d1",
                0.9,
                "/STATUTE/CRIMINAL",
                "第三十三条 刑罚分为主刑和附加刑。",
                "第三十三条 刑罚分为主刑和附加刑。",
                "刑法",
                "chunk-1"
        );
        session.recordSearch(
                new RagSearchResult(
                        List.of(semanticHit),
                        null,
                        Map.of("fulltextCount", 1, "rrfCount", 1)
                ),
                20L
        );

        assertEquals(1, session.hits().size());
        assertEquals("chunk-1", session.hits().get(0).chunkId());
        assertTrue(session.lastRetrievalTrace().containsKey("fulltextCount"));
    }
}
