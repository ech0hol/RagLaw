package com.raglaw.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.messaging.ParseMessagePublisher;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.search.ElasticsearchRetriever;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceListTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository documentChunkRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private DocumentStorageService documentStorageService;
    @Mock
    private IngestService ingestService;
    @Mock
    private ParseMessagePublisher parseMessagePublisher;
    @Mock
    private RagProperties ragProperties;
    @Mock
    private DocumentTitleLockService documentTitleLockService;
    @Mock
    private ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider;

    private DocumentUploadService service;

    @BeforeEach
    void setUp() {
        service = new DocumentUploadService(
                documentRepository,
                documentChunkRepository,
                categoryService,
                documentStorageService,
                ingestService,
                parseMessagePublisher,
                ragProperties,
                documentTitleLockService,
                elasticsearchRetrieverProvider
        );
    }

    @Test
    void listByCategoryPathUsesPrefixForL2() {
        DocumentEntity document = new DocumentEntity(
                "doc-1", "cat-1", "法规", "STATUTE", "user-1", "key.md"
        );
        document.setStatus(DocStatus.INDEXED);
        when(documentRepository.countAdminList(
                eq("/STATUTE/CIVIL"),
                eq(false),
                eq(null),
                eq(true)
        )).thenReturn(1L);
        when(documentRepository.findAdminList(
                eq("/STATUTE/CIVIL"),
                eq(false),
                eq(null),
                eq(true),
                eq(20),
                eq(0)
        )).thenReturn(List.of(document));

        var page = service.listByCategoryPath("/STATUTE/CIVIL", null, 0, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).title()).isEqualTo("法规");
    }

    @Test
    void listByCategoryPathUsesExactMatchForL3() {
        when(documentRepository.countAdminList(
                eq("/STATUTE/CIVIL/LABOR"),
                eq(true),
                eq(null),
                eq(true)
        )).thenReturn(0L);
        when(documentRepository.findAdminList(
                eq("/STATUTE/CIVIL/LABOR"),
                eq(true),
                eq(null),
                eq(true),
                eq(20),
                eq(0)
        )).thenReturn(List.of());

        var page = service.listByCategoryPath("/STATUTE/CIVIL/LABOR", null, 0, 20);

        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
    }

    @Test
    void categoryDocumentCountsMapsRows() {
        when(documentRepository.countDocumentsByCategory()).thenReturn(List.<Object[]>of(
                new Object[] { "cat-1", "/STATUTE/CIVIL/LABOR", 3L }
        ));

        var counts = service.categoryDocumentCounts();

        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).categoryId()).isEqualTo("cat-1");
        assertThat(counts.get(0).path()).isEqualTo("/STATUTE/CIVIL/LABOR");
        assertThat(counts.get(0).count()).isEqualTo(3L);
    }
}
