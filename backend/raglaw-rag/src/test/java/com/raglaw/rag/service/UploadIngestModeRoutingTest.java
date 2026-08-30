package com.raglaw.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.DocumentUploadBatchResultDto;
import com.raglaw.rag.messaging.ParseMessagePublisher;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.search.ElasticsearchRetriever;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class UploadIngestModeRoutingTest {

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

    private DocumentUploadService service;
    private RagProperties ragProperties;
    private CategoryEntity statuteCategory;
    private CategoryEntity contractCategory;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
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
        statuteCategory = new CategoryEntity("cat-statute", "cat-l2", 3, "labor", "Labor", "/STATUTE/CIVIL/LABOR", "STATUTE", 1);
        contractCategory = new CategoryEntity("cat-contract", "cat-l2", 3, "contract", "Contract", "/CONTRACT", "CONTRACT", 1);
        when(documentTitleLockService.withTitleLock(any(), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(documentRepository.findByTitle(any())).thenReturn(List.of());
    }

    @Test
    void singleStatuteUploadUsesSyncMode() throws Exception {
        when(categoryService.findEntity("cat-statute")).thenReturn(statuteCategory);
        stubStore();
        when(documentRepository.findById(any())).thenAnswer(inv -> {
            DocumentEntity saved = new DocumentEntity("doc-1", "cat-statute", "law", "STATUTE", "user", "key.md");
            return java.util.Optional.of(saved);
        });

        DocumentUploadBatchResultDto result = service.uploadBatch(List.of(file("law.md")), "cat-statute");

        assertThat(result.ingestMode()).isEqualTo("SYNC");
        verify(ingestService).ingest(any(DocumentEntity.class));
        verify(parseMessagePublisher, never()).publishParseJob(any());
    }

    @Test
    void contractBatchUsesAsyncModeWhenRabbitEnabled() throws Exception {
        ragProperties.getRabbit().setEnabled(true);
        when(categoryService.findEntity("cat-contract")).thenReturn(contractCategory);
        stubStore();
        when(documentRepository.findById(any())).thenAnswer(inv -> {
            DocumentEntity saved = new DocumentEntity("doc-1", "cat-contract", "contract", "CONTRACT", "user", "key.pdf");
            return java.util.Optional.of(saved);
        });

        DocumentUploadBatchResultDto result = service.uploadContractsBatch(List.of(file("contract.pdf")), "cat-contract");

        assertThat(result.ingestMode()).isEqualTo("ASYNC");
        verify(parseMessagePublisher).publishParseJob(any());
        verify(ingestService, never()).ingest(any());
    }

    private void stubStore() throws Exception {
        when(documentStorageService.store(any(), any(), any(), any(Long.class), any()))
                .thenReturn("uploads/key.md");
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static MultipartFile file(String name) {
        return new MockMultipartFile("file", name, "text/plain", "content".getBytes(StandardCharsets.UTF_8));
    }
}
