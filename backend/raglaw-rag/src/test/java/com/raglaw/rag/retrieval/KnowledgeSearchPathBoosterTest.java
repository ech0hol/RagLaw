package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.rag.retrieval.FullTextScoreFilter.ScoredRow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeSearchPathBoosterTest {

    private final KnowledgeSearchPathBooster booster = new KnowledgeSearchPathBooster();

    @Test
    void boostedScore_prefersStatuteForOvertimeQuery() {
        double statute = KnowledgeSearchPathBooster.boostedScore(
                "加班费",
                "/STATUTE/CIVIL/LABOR",
                2.0
        );
        double caseScore = KnowledgeSearchPathBooster.boostedScore(
                "加班费",
                "/CASE/CIVIL/LABOR",
                2.0
        );
        assertThat(statute).isGreaterThan(caseScore);
    }

    @Test
    void boostedScore_prefersCivilForLoanInterestQuery() {
        double civil = KnowledgeSearchPathBooster.boostedScore(
                "借贷利息",
                "/STATUTE/CIVIL/CONTRACT",
                2.0
        );
        double criminal = KnowledgeSearchPathBooster.boostedScore(
                "借贷利息",
                "/STATUTE/CRIMINAL/GENERAL",
                2.0
        );
        assertThat(civil).isGreaterThan(criminal);
    }

    @Test
    void rerank_movesPreferredPathToTop() {
        List<ScoredRow> rows = List.of(
                new ScoredRow("case-doc", "/CASE/CIVIL/LABOR", 3.0),
                new ScoredRow("statute-doc", "/STATUTE/CIVIL/LABOR", 2.5)
        );
        List<ScoredRow> reranked = booster.rerank(
                "加班费",
                rows,
                Map.of(
                        "case-doc", "/CASE/CIVIL/LABOR",
                        "statute-doc", "/STATUTE/CIVIL/LABOR"
                )
        );
        assertThat(reranked.get(0).documentId()).isEqualTo("statute-doc");
    }
}
