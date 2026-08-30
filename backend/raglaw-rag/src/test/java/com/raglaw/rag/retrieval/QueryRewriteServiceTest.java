package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryRewriteServiceTest {

    private QueryRewriteService service;

    @BeforeEach
    void setUp() {
        service = new QueryRewriteService();
    }

    @Test
    void rewritesChemicalKeyword() {
        String rewritten = service.rewrite("化学");

        assertThat(rewritten).contains("化学");
        assertThat(rewritten).contains("危险化学品");
    }

    @Test
    void rewritesChemicalPurchaseQuestion() {
        String rewritten = service.rewrite("我想买化学品是违法的吗");

        assertThat(rewritten).contains("危险化学品");
        assertThat(rewritten).contains("购买");
    }

    @Test
    void keepsCoreTermsForLaborQuestion() {
        String rewritten = service.rewrite("公司拖欠工资如何维权");

        assertThat(rewritten).contains("拖欠工资");
    }

    @Test
    void rewritesAdditionalPunishmentTowardArticle34() {
        String rewritten = service.rewriteForArticleRetrieval("附加刑的种类");

        assertThat(rewritten).contains("第三十四条");
        assertThat(rewritten).contains("附加刑");
    }
}
