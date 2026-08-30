package com.raglaw.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.rag.dto.KnowledgeHitDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeSearchServiceTest {

    @Mock
    private DocumentKnowledgeSearchService documentKnowledgeSearchService;

    private KnowledgeSearchService knowledgeSearchService;

    @BeforeEach
    void setUp() {
        knowledgeSearchService = new KnowledgeSearchService(documentKnowledgeSearchService);
    }

    @Test
    void searchPageDelegatesToDocumentKnowledgeSearch() {
        KnowledgeHitDto hit = new KnowledgeHitDto(
                "doc-1",
                "doc-1",
                "劳动法节选",
                "/STATUTE/CIVIL/LABOR",
                "拖欠工资条款",
                1.5,
                Instant.parse("2024-08-27T00:00:00Z"),
                "2025-12-27"
        );
        when(documentKnowledgeSearchService.search(
                eq("拖欠工资"),
                eq(List.of("STATUTE")),
                eq("STATUTE"),
                eq(null),
                eq(20)
        )).thenReturn(List.of(hit));

        List<KnowledgeHitDto> hits = knowledgeSearchService.search("拖欠工资", "STATUTE", 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).documentId()).isEqualTo("doc-1");
        assertThat(hits.get(0).path()).isEqualTo("/STATUTE/CIVIL/LABOR");
        assertThat(hits.get(0).effectiveDate()).isEqualTo("2025-12-27");
        verify(documentKnowledgeSearchService).search("拖欠工资", List.of("STATUTE"), "STATUTE", null, 20);
    }

    @Test
    void searchPageWithBlankQueryReturnsEmpty() {
        var page = knowledgeSearchService.searchPage("", "STATUTE", null, 0, 10);
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }
}
