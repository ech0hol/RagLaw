package com.raglaw.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.CategoryRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeCatalogServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private KnowledgeCatalogService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeCatalogService(documentRepository, categoryRepository);
    }

    @Test
    void buildCatalogHitsIncludesSummaryAndDocuments() {
        when(documentRepository.countByDocTypeAndStatus("STATUTE", DocStatus.INDEXED)).thenReturn(2L);
        when(documentRepository.countByDocTypeAndStatus("CASE", DocStatus.INDEXED)).thenReturn(1L);
        when(documentRepository.countDocumentsByCategory()).thenReturn(List.<Object[]>of(
                new Object[] {"c1", "/STATUTE/CIVIL", 2L}
        ));

        DocumentEntity statute = document("d1", "中华人民共和国民法典", "STATUTE", "c1");
        when(documentRepository.findByDocTypeAndStatus("STATUTE", DocStatus.INDEXED))
                .thenReturn(List.of(statute));
        when(documentRepository.findByDocTypeAndStatus("CASE", DocStatus.INDEXED))
                .thenReturn(List.of());
        when(categoryRepository.findById("c1")).thenReturn(java.util.Optional.of(category("c1", "/STATUTE/CIVIL")));

        List<RagSearchHit> hits = service.buildCatalogHits(List.of(), 10);

        assertTrue(hits.size() >= 2);
        assertEquals("知识库概览", hits.get(0).title());
        assertTrue(hits.get(0).llmContentOrExcerpt().contains("法规 2 部"));
        assertEquals("中华人民共和国民法典", hits.get(1).title());
    }

    private static DocumentEntity document(String id, String title, String docType, String categoryId) {
        DocumentEntity entity = new DocumentEntity(id, categoryId, title, docType, null, null);
        entity.setStatus(DocStatus.INDEXED);
        return entity;
    }

    private static CategoryEntity category(String id, String path) {
        return new CategoryEntity(id, null, 2, "CIVIL", "民事", path, "STATUTE", 1);
    }
}
