package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CitationRelevanceFilterTest {

    private CitationRelevanceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CitationRelevanceFilter(new RagProperties());
    }

    @Test
    void filtersConstitutionalNoiseForMedicalInsuranceQuery() {
        List<RagSearchHit> hits = List.of(
                new RagSearchHit("c1", "d1", 8.0, "/STATUTE/SOCIAL/GENERAL", "医保报销", "医保报销", "社会救助法"),
                new RagSearchHit("c2", "d2", 7.0, "/STATUTE/CONSTITUTIONAL/GENERAL", "居民委员会", "居民委员会", "宪法"),
                new RagSearchHit("c3", "d3", 6.0, "/STATUTE/SOCIAL/GENERAL", "社会保险", "社会保险", "社会保险法")
        );

        List<RagSearchHit> filtered = filter.filter(hits, "城乡居民医保可以异地报销吗");

        assertThat(filtered).extracting(RagSearchHit::title)
                .contains("社会救助法", "社会保险法")
                .doesNotContain("宪法");
    }

    @Test
    void keepsTopHitWhenScoreSpreadIsWide() {
        List<RagSearchHit> hits = List.of(
                new RagSearchHit("c1", "d1", 10.0, "/STATUTE/CRIMINAL/GENERAL", "刑罚", "刑罚", "刑法"),
                new RagSearchHit("c2", "d2", 1.0, "/STATUTE/CIVIL/GENERAL", "合同", "合同", "民法典")
        );

        List<RagSearchHit> filtered = filter.filter(hits, "刑法中刑罚种类");

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).title()).isEqualTo("刑法");
    }

    @Test
    void limitsVisibleCitationsToFive() {
        List<RagSearchHit> hits = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            hits.add(new RagSearchHit(
                    "c" + i,
                    "d" + i,
                    10.0 - i,
                    "/STATUTE/SOCIAL/GENERAL",
                    "excerpt " + i,
                    "excerpt " + i,
                    "doc " + i
            ));
        }

        assertThat(filter.filter(hits, "社保")).hasSize(5);
    }
}
