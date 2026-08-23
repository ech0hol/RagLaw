package com.raglaw.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.contract.ContractClassifier;
import com.raglaw.rag.contract.ContractRiskAnalyzer;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.domain.IngestStage;
import com.raglaw.rag.ingest.DocumentTextExtractor;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class IngestPipelineTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository documentChunkRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private DocumentStorageService documentStorageService;
    @Mock
    private DocumentTextExtractor documentTextExtractor;
    @Mock
    private ContractClassifier contractClassifier;
    @Mock
    private ContractRiskAnalyzer contractRiskAnalyzer;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private ObjectProvider<VectorStoreService> vectorStoreService;
    @Mock
    private CaseStatuteLinker caseStatuteLinker;

    private IngestPipeline pipeline;
    private DocumentEntity document;

    @BeforeEach
    void setUp() {
        pipeline = new IngestPipeline(
                documentRepository,
                documentChunkRepository,
                categoryService,
                documentStorageService,
                documentTextExtractor,
                contractClassifier,
                contractRiskAnalyzer,
                embeddingService,
                vectorStoreService,
                caseStatuteLinker,
                new ObjectMapper()
        );
        document = new DocumentEntity("doc-1", "cat-l3", "test", "STATUTE", "user-1", "key.md");
        document.setStatus(DocStatus.PENDING);

        CategoryEntity l3 = new CategoryEntity("cat-l3", "cat-l2", 3, "l3", "L3", "/STATUTE/CIVIL/LABOR", "STATUTE", 1);
        CategoryEntity l2 = new CategoryEntity("cat-l2", "cat-l1", 2, "l2", "L2", "/STATUTE/CIVIL", "STATUTE", 1);
        CategoryEntity l1 = new CategoryEntity("cat-l1", null, 1, "l1", "L1", "/STATUTE", "STATUTE", 1);

        when(categoryService.findEntity("cat-l3")).thenReturn(l3);
        when(categoryService.findEntity("cat-l2")).thenReturn(l2);
        when(categoryService.findEntity("cat-l1")).thenReturn(l1);
        when(documentStorageService.load(anyString()))
                .thenReturn(new ByteArrayInputStream("## 标题\n\n正文。".getBytes(StandardCharsets.UTF_8)));
        when(documentTextExtractor.extractFromStorageKey(any(), anyString()))
                .thenReturn(new DocumentTextExtractor.ExtractionResult("## 标题\n\n正文。", "markdown", false));
        when(vectorStoreService.getIfAvailable()).thenReturn(null);
    }

    @Test
    void sync_setsIndexedStatus() {
        pipeline.sync(document);
        assertEquals(DocStatus.INDEXED, document.getStatus());
        assertEquals(IngestStage.INDEXED, document.getIngestStage());
        verify(documentChunkRepository).deleteByDocumentId("doc-1");
    }
}
