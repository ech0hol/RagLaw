package com.raglaw.rag.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IngestService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractTextServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private ContractRiskRepository riskRepository;
    @Mock
    private IngestService ingestService;

    private ContractTextService service;

    @BeforeEach
    void setUp() {
        service = new ContractTextService(documentRepository, chunkRepository, riskRepository, ingestService);
    }

    @Test
    void buildFullText_appliesAcceptedRevisionInline() {
        String docId = "doc-1";
        String chunkId = "chunk-1";
        DocumentChunkEntity chunk = new DocumentChunkEntity(
                chunkId, docId, null, 0, "甲方应支付违约金十万元。", "/C", "/C/C", "/C/C/G", null
        );
        ContractRiskEntity risk = new ContractRiskEntity(
                "risk-1", docId, chunkId, "MEDIUM", "违约责任", "违约金", "十万元", "五万元"
        );
        risk.setAccepted(true);
        risk.setRevisedExcerpt("甲方应支付违约金五万元。");

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(List.of(chunk));
        when(riskRepository.findByDocumentIdOrderByCreatedAtAsc(docId)).thenReturn(List.of(risk));

        String text = service.buildFullText(docId);
        assertFalse(text.contains("修订建议（已采纳）"));
        assertTrue(text.contains("五万元"));
        assertFalse(text.contains("十万元"));
    }
}
