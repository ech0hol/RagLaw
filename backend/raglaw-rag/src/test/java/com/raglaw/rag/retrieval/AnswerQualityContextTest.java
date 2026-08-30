package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.rag.dto.RetrievalHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerQualityContextTest {

    @Test
    void diversifiedHitsPreferMultipleDocuments() {
        RetrievalDiversifier diversifier = new RetrievalDiversifier();
        List<RetrievalHit> ranked = List.of(
                hit("c1", "doc-a"),
                hit("c2", "doc-a"),
                hit("c3", "doc-a"),
                hit("c4", "doc-b"),
                hit("c5", "doc-c")
        );

        List<RetrievalHit> diversified = diversifier.diversify(ranked, 5, 2);

        long distinctDocuments = diversified.stream().map(RetrievalHit::documentId).distinct().count();
        assertThat(distinctDocuments).isGreaterThanOrEqualTo(2);
    }

    @Test
    void rewrittenChemicalQueryContainsLegalTerms() {
        QueryRewriteService rewriteService = new QueryRewriteService();
        String rewritten = rewriteService.rewrite("我想买化学品是违法的吗");

        assertThat(rewritten).containsAnyOf("危险化学品", "购买");
    }

    private static RetrievalHit hit(String chunkId, String documentId) {
        return new RetrievalHit(
                chunkId,
                documentId,
                "x".repeat(900),
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR",
                1.0
        );
    }
}
