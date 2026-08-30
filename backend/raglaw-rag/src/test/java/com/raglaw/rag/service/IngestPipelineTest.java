package com.raglaw.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.contract.ContractClassifier;
import com.raglaw.rag.contract.ContractRiskAnalyzer;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.domain.IngestStage;
import com.raglaw.rag.ingest.DocumentTextExtractor;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import com.raglaw.rag.search.ElasticsearchRetriever;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
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
    private ObjectProvider<ElasticsearchRetriever> elasticsearchRetriever;
    @Mock
    private ElasticsearchRetriever elasticsearchRetrieverBean;
    @Mock
    private IndexOutboxService indexOutboxService;
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
                elasticsearchRetriever,
                indexOutboxService,
                caseStatuteLinker,
                new ObjectMapper()
        );
        document = new DocumentEntity("doc-1", "cat-l3", "test", "STATUTE", "user-1", "key.md");
        document.setStatus(DocStatus.PENDING);

        CategoryEntity l3 = new CategoryEntity("cat-l3", "cat-l2", 3, "l3", "L3", "/STATUTE/CIVIL/LABOR", "STATUTE", 1);
        CategoryEntity l2 = new CategoryEntity("cat-l2", "cat-l1", 2, "l2", "L2", "/STATUTE/CIVIL", "STATUTE", 1);
        CategoryEntity l1 = new CategoryEntity("cat-l1", null, 1, "l1", "L1", "/STATUTE", "STATUTE", 1);

        lenient().when(categoryService.findEntity("cat-l3")).thenReturn(l3);
        lenient().when(categoryService.findEntity("cat-l2")).thenReturn(l2);
        lenient().when(categoryService.findEntity("cat-l1")).thenReturn(l1);
        lenient().when(documentStorageService.load(anyString()))
                .thenReturn(new ByteArrayInputStream("## 标题\n\n正文。".getBytes(StandardCharsets.UTF_8)));
        lenient().when(documentTextExtractor.extractFromStorageKey(any(), anyString()))
                .thenReturn(new DocumentTextExtractor.ExtractionResult("## 标题\n\n正文。", "markdown", false));
        lenient().when(elasticsearchRetriever.getIfAvailable()).thenReturn(null);
    }

    @Test
    void sync_setsIndexedStatus() {
        pipeline.sync(document);
        assertEquals(DocStatus.INDEXED, document.getStatus());
        assertEquals(IngestStage.INDEXED, document.getIngestStage());
        assertEquals("## 标题\n\n正文。", document.getFullText());
        verify(documentChunkRepository).deleteByDocumentId("doc-1");
        verify(indexOutboxService, never()).enqueueDeleteDocument(anyString(), anyLong());
    }

    @Test
    void parse_doesNotEnqueueDeleteWhenElasticsearchDisabled() {
        pipeline.parse(document);

        verify(documentChunkRepository).deleteByDocumentId("doc-1");
        verify(indexOutboxService, never()).enqueueDeleteDocument(anyString(), anyLong());
    }

    @Test
    void index_enqueuesUpsertWhenElasticsearchEnabled() throws Exception {
        when(elasticsearchRetriever.getIfAvailable()).thenReturn(elasticsearchRetrieverBean);
        when(elasticsearchRetrieverBean.isEnabled()).thenReturn(true);
        when(embeddingService.isEnabled()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(Optional.of(new float[] {0.1f, 0.2f}));
        DocumentChunkEntity chunk = new DocumentChunkEntity(
                "chunk-1",
                "doc-1",
                "parent-1",
                ChunkLevel.MICRO,
                0,
                "正文内容超过短标题。",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR",
                null
        );
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc("doc-1")).thenReturn(List.of(chunk));

        document.setIngestStage(IngestStage.PARSED);
        pipeline.index(document);

        verify(elasticsearchRetrieverBean).deleteByDocumentIdOrThrow("doc-1");
        verify(elasticsearchRetrieverBean).bulkIndexDocumentOrThrow(
                eq("doc-1"),
                eq("STATUTE"),
                anyList(),
                anyLong()
        );
        verify(indexOutboxService, never()).enqueueBulkUpsert(anyString(), anyString(), anyList(), anyLong());
    }

    @Test
    void index_marksFailedWhenElasticsearchWriteFails() throws IOException {
        when(elasticsearchRetriever.getIfAvailable()).thenReturn(elasticsearchRetrieverBean);
        when(elasticsearchRetrieverBean.isEnabled()).thenReturn(true);
        when(embeddingService.isEnabled()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(Optional.of(new float[] {0.1f, 0.2f}));
        DocumentChunkEntity chunk = new DocumentChunkEntity(
                "chunk-1",
                "doc-1",
                "parent-1",
                ChunkLevel.MICRO,
                0,
                "正文内容超过短标题。",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR",
                null
        );
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc("doc-1")).thenReturn(List.of(chunk));
        doThrow(new IOException("ES unavailable")).when(elasticsearchRetrieverBean).deleteByDocumentIdOrThrow("doc-1");

        document.setIngestStage(IngestStage.PARSED);
        pipeline.index(document);

        assertEquals(IngestStage.FAILED, document.getIngestStage());
        assertEquals("ES unavailable", document.getIngestError());
    }

    @Test
    void index_runsContractAnalysisForContractDocuments() {
        DocumentEntity contract = new DocumentEntity("doc-contract", "cat-l3", "合同", "CONTRACT", "user-1", "key.md");
        contract.setStatus(DocStatus.INDEXED);
        contract.setIngestStage(IngestStage.PARSED);

        pipeline.index(contract);

        verify(contractRiskAnalyzer).analyze("doc-contract");
    }

    @Test
    void index_skipsEmbeddingForContractDocuments() {
        DocumentEntity contract = new DocumentEntity("doc-contract", "cat-l3", "合同", "CONTRACT", "user-1", "key.md");
        contract.setStatus(DocStatus.PENDING);
        contract.setIngestStage(IngestStage.PARSED);

        pipeline.index(contract);

        verify(embeddingService, never()).embed(anyString());
        verify(contractRiskAnalyzer).analyze("doc-contract");
        assertEquals(IngestStage.INDEXED, contract.getIngestStage());
    }
}
