package com.raglaw.rag.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.ingest.PdfPageLocator;
import com.raglaw.rag.ingest.PdfTextLocator;
import com.raglaw.rag.llm.LlmChatClient;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractRiskAnalyzerTest {

    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private ContractRiskRepository riskRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentStorageService documentStorageService;
    @Mock
    private PdfPageLocator pdfPageLocator;
    @Mock
    private PdfTextLocator pdfTextLocator;
    @Mock
    private LlmChatClient llmChatClient;
    @Mock
    private ContractRagContextBuilder ragContextBuilder;

    private ContractRiskAnalyzer analyzer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RagProperties properties = new RagProperties();
        properties.getContract().setChunksPerBatch(4);
        properties.getContract().setMaxRisksPerDocument(10);
        analyzer = new ContractRiskAnalyzer(
                chunkRepository,
                riskRepository,
                documentRepository,
                documentStorageService,
                pdfPageLocator,
                pdfTextLocator,
                objectMapper,
                llmChatClient,
                properties,
                ragContextBuilder,
                null
        );
    }

    @Test
    void analyze_mapsLlmRisksToEntities() throws Exception {
        String docId = "doc-1";
        DocumentEntity document = new DocumentEntity(docId, "cat", "测试合同", "CONTRACT", "user", "key.md");
        document.setMetadataJson("{\"suggestedAgentCode\":\"CONTRACT_CIVIL\"}");

        DocumentChunkEntity chunk = new DocumentChunkEntity(
                "chunk-1", docId, null, ChunkLevel.CHILD, 0,
                "违约方应支付合同总价30%的违约金。", "/C", "/C/C", "/C/C/G", null
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(List.of(chunk));
        when(ragContextBuilder.build(any(), any(), any())).thenReturn(
                new ContractRagContextBuilder.ContractRagContext("RAG", List.of(), 0, List.of())
        );
        when(ragContextBuilder.buildForBatch(any(), any(), any(), any())).thenReturn(
                new ContractRagContextBuilder.ContractRagContext("RAG", List.of(), 0, List.of())
        );
        when(llmChatClient.completeJson(any(), any(), any())).thenReturn("""
                {
                  "risks": [
                    {
                      "chunkIndex": 0,
                      "severity": "HIGH",
                      "dimension": "违约责任",
                      "summary": "违约金过高",
                      "excerpt": "违约方应支付合同总价30%的违约金",
                      "suggestion": "建议调整为合理比例",
                      "legalBasis": ["劳动合同法"]
                    }
                  ]
                }
                """);
        when(riskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ContractRiskEntity> risks = analyzer.analyze(docId);

        assertThat(risks).hasSize(1);
        ContractRiskEntity risk = risks.get(0);
        assertThat(risk.getSeverity()).isEqualTo("HIGH");
        assertThat(risk.getSummary()).isEqualTo("违约金过高");
        assertThat(risk.getExcerpt()).contains("30%");
        assertThat(risk.getChunkId()).isEqualTo("chunk-1");

        ArgumentCaptor<DocumentEntity> savedDoc = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).save(savedDoc.capture());
        assertThat(savedDoc.getValue().getMetadataJson()).contains("contractAnalysisSource");
        assertThat(savedDoc.getValue().getMetadataJson()).contains(ContractReviewStatus.COMPLETED);
    }

    @Test
    void analyze_persistsRagHitCountInMetadata() throws Exception {
        String docId = "doc-rag";
        DocumentEntity document = new DocumentEntity(docId, "cat", "测试合同", "CONTRACT", "user", "key.md");
        document.setMetadataJson("{\"suggestedAgentCode\":\"CONTRACT_CIVIL\"}");
        DocumentChunkEntity chunk = new DocumentChunkEntity(
                "chunk-1", docId, null, ChunkLevel.CHILD, 0,
                "违约方应支付合同总价30%的违约金。", "/C", "/C/C", "/C/C/G", null
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(List.of(chunk));
        when(ragContextBuilder.build(any(), any(), any())).thenReturn(
                new ContractRagContextBuilder.ContractRagContext("RAG", List.of(), 3, List.of())
        );
        when(ragContextBuilder.buildForBatch(any(), any(), any(), any())).thenReturn(
                new ContractRagContextBuilder.ContractRagContext("RAG", List.of(), 1, List.of())
        );
        when(llmChatClient.completeJson(any(), any(), any())).thenReturn("""
                {"risks":[{"chunkIndex":0,"severity":"HIGH","dimension":"违约责任","summary":"违约金过高","excerpt":"30%","suggestion":"调整","legalBasis":[]}]}
                """);
        when(riskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        analyzer.analyze(docId);

        ArgumentCaptor<DocumentEntity> savedDoc = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).save(savedDoc.capture());
        DocumentEntity lastSaved = savedDoc.getAllValues().get(savedDoc.getAllValues().size() - 1);
        assertThat(lastSaved.getMetadataJson()).contains("\"contractRagHitCount\":3");
    }

    @Test
    void analyze_skipsLlmWhenNoChunks() {
        String docId = "doc-empty";
        DocumentEntity document = new DocumentEntity(docId, "cat", "空合同", "CONTRACT", "user", "key.md");

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(List.of());

        List<ContractRiskEntity> risks = analyzer.analyze(docId);

        assertThat(risks).isEmpty();
        verify(llmChatClient, org.mockito.Mockito.never()).completeJson(any(), any(), any());
        ArgumentCaptor<DocumentEntity> savedDoc = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).save(savedDoc.capture());
        DocumentEntity lastSaved = savedDoc.getAllValues().get(savedDoc.getAllValues().size() - 1);
        assertThat(lastSaved.getMetadataJson()).contains(ContractReviewStatus.SKIPPED_NO_CHUNKS);
        assertThat(lastSaved.getMetadataJson()).doesNotContain("contractAnalysisModel");
    }

    @Test
    void analyze_failsWhenAllBatchesFail() throws Exception {
        String docId = "doc-fail";
        DocumentEntity document = new DocumentEntity(docId, "cat", "测试合同", "CONTRACT", "user", "key.md");
        DocumentChunkEntity chunk = new DocumentChunkEntity(
                "chunk-1", docId, null, ChunkLevel.CHILD, 0,
                "违约方应支付合同总价30%的违约金。", "/C", "/C/C", "/C/C/G", null
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(List.of(chunk));
        when(ragContextBuilder.build(any(), any(), any())).thenReturn(
                new ContractRagContextBuilder.ContractRagContext("RAG", List.of(), 0, List.of())
        );
        when(ragContextBuilder.buildForBatch(any(), any(), any(), any())).thenReturn(
                new ContractRagContextBuilder.ContractRagContext("RAG", List.of(), 0, List.of())
        );
        when(llmChatClient.completeJson(any(), any(), any())).thenThrow(new IllegalStateException("API key missing"));

        List<ContractRiskEntity> risks = analyzer.analyze(docId);

        assertThat(risks).isEmpty();
        ArgumentCaptor<DocumentEntity> savedDoc = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).save(savedDoc.capture());
        DocumentEntity lastSaved = savedDoc.getAllValues().get(savedDoc.getAllValues().size() - 1);
        assertThat(lastSaved.getMetadataJson()).contains(ContractReviewStatus.FAILED);
    }

    @Test
    void analyze_retriesTransientLlmErrors() throws Exception {
        String docId = "doc-retry";
        DocumentEntity document = new DocumentEntity(docId, "cat", "测试合同", "CONTRACT", "user", "key.md");
        DocumentChunkEntity chunk = new DocumentChunkEntity(
                "chunk-1", docId, null, ChunkLevel.CHILD, 0,
                "违约方应支付合同总价30%的违约金。", "/C", "/C/C", "/C/C/G", null
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(List.of(chunk));
        when(ragContextBuilder.build(any(), any(), any())).thenReturn(
                new ContractRagContextBuilder.ContractRagContext("RAG", List.of(), 0, List.of())
        );
        when(ragContextBuilder.buildForBatch(any(), any(), any(), any())).thenReturn(
                new ContractRagContextBuilder.ContractRagContext("RAG", List.of(), 0, List.of())
        );
        when(llmChatClient.completeJson(any(), any(), any()))
                .thenThrow(new IllegalStateException("Connection reset"))
                .thenReturn("{\"risks\":[]}");
        when(riskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ContractRiskEntity> risks = analyzer.analyze(docId);

        assertThat(risks).isEmpty();
        verify(llmChatClient, times(2)).completeJson(any(), any(), any());
    }

    @Test
    void resolveExcerpt_findsLongestMatchingPrefix() {
        String chunk = "甲方应支付违约金十万元。";
        assertThat(ContractRiskAnalyzer.resolveExcerpt(chunk, "违约金十万元")).isEqualTo("违约金十万元");
        assertThat(ContractRiskAnalyzer.resolveExcerpt(chunk, "不存在的句子")).isEmpty();
    }
}
