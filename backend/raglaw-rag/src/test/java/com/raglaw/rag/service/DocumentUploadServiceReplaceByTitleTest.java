package com.raglaw.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.messaging.ParseMessagePublisher;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.search.ElasticsearchRetriever;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceReplaceByTitleTest {

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
    private DocumentTitleLockService documentTitleLockService;
    @Mock
    private ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider;
    @Mock
    private ElasticsearchRetriever elasticsearchRetriever;

    private DocumentUploadService service;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        RagProperties ragProperties = new RagProperties();
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
        category = new CategoryEntity("cat-statute", "cat-l2", 3, "labor", "Labor", "/STATUTE/CIVIL/LABOR", "STATUTE", 1);
        when(documentTitleLockService.withTitleLock(eq("law"), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(categoryService.findEntity("cat-statute")).thenReturn(category);
        when(elasticsearchRetrieverProvider.getIfAvailable()).thenReturn(elasticsearchRetriever);
        when(elasticsearchRetriever.isEnabled()).thenReturn(true);
    }

    @Test
    void uploadReusesDocumentIdAndBumpsIndexVersion() throws Exception {
        DocumentEntity existing = new DocumentEntity("doc-existing", "cat-statute", "law", "STATUTE", "user", "old.md");
        existing.bumpIndexVersion();
        existing.bumpIndexVersion();
        when(documentRepository.findByTitle("law")).thenReturn(List.of(existing));
        when(documentRepository.findById("doc-existing")).thenReturn(Optional.of(existing));
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentStorageService.store(any(), any(), any(), any(Long.class), any()))
                .thenReturn("uploads/new.md");

        var dto = service.upload(new MockMultipartFile(
                "file",
                "law.md",
                "text/markdown",
                "# updated".getBytes(StandardCharsets.UTF_8)
        ), "cat-statute");

        assertThat(dto.id()).isEqualTo("doc-existing");
        assertThat(existing.getIndexVersion()).isEqualTo(3L);
        verify(documentChunkRepository).deleteByDocumentId("doc-existing");
        verify(elasticsearchRetriever).deleteByDocumentId("doc-existing");
        verify(ingestService).ingest(existing);
    }
}
