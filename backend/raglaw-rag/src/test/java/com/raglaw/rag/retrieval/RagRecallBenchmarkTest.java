package com.raglaw.rag.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.raglaw.rag.dto.RetrievalHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagRecallBenchmarkTest {

    private final RetrievalReranker reranker = new RetrievalReranker();

    @Test
    void statuteAgentPrefersStatutePathForWageQuery() {
        List<RetrievalHit> hits = List.of(
                caseHit("case-1", 0.95),
                statuteHit("stat-1", 0.90)
        );

        List<RetrievalHit> reranked = reranker.rerank(hits, "STATUTE_CIVIL");

        assertEquals("stat-1", reranked.get(0).chunkId());
    }

    @Test
    void statuteBoostBeatsModeratelyHigherCaseScore() {
        List<RetrievalHit> hits = List.of(
                caseHit("case-1", 0.85),
                statuteHit("stat-1", 0.75)
        );

        List<RetrievalHit> reranked = reranker.rerank(hits, "STATUTE_CIVIL");

        assertEquals("stat-1", reranked.get(0).chunkId());
    }

    private static RetrievalHit caseHit(String chunkId, double score) {
        return new RetrievalHit(chunkId, "doc-case", "案例叙述", "/CASE", "CIVIL", "LABOR", score);
    }

    private static RetrievalHit statuteHit(String chunkId, double score) {
        return new RetrievalHit(chunkId, "doc-stat", "法规条文", "/STATUTE", "CIVIL", "LABOR", score);
    }
}
