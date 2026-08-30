package com.raglaw.rag.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ContractAccessService contractAccessService;
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
        service = new ContractTextService(
                contractAccessService,
                documentRepository,
                chunkRepository,
                riskRepository,
                ingestService,
                new ObjectMapper()
        );
    }

    @Test
    void buildFullText_appliesAcceptedRevisionInline() {
        String docId = "doc-1";
        String chunkId = "chunk-1";
        DocumentChunkEntity chunk = new DocumentChunkEntity(
                chunkId, docId, null, com.raglaw.rag.domain.ChunkLevel.CHILD, 0,
                "甲方应支付违约金十万元。", "/C", "/C/C", "/C/C/G", null
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

    @Test
    void getText_returnsDisplayChunksOrderedByIndex() {
        String docId = "doc-1";
        DocumentEntity document = new DocumentEntity(
                docId, "cat-1", "合同", "CONTRACT", "user-1", "uploads/contract.pdf"
        );
        DocumentChunkEntity parent = new DocumentChunkEntity(
                "parent-1", docId, null, ChunkLevel.PARENT, 0,
                "段落组 1", "/C", "/C/C", "/C/C/G", null
        );
        DocumentChunkEntity chunk0 = new DocumentChunkEntity(
                "chunk-0", docId, null, ChunkLevel.CHILD, 1,
                "第一条 租赁标的。", "/C", "/C/C", "/C/C/G", null
        );
        DocumentChunkEntity chunk1 = new DocumentChunkEntity(
                "chunk-1", docId, null, ChunkLevel.CHILD, 2,
                "第二条 租金支付。", "/C", "/C/C", "/C/C/G", null
        );

        when(contractAccessService.requireOwnedContract(docId)).thenReturn(document);
        when(ingestService.resolveOriginalFilename(document)).thenReturn("contract.pdf");
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId))
                .thenReturn(java.util.List.of(parent, chunk0, chunk1));
        when(riskRepository.findByDocumentIdOrderByCreatedAtAsc(docId)).thenReturn(java.util.List.of());

        var dto = service.getText(docId);

        assertEquals(2, dto.chunks().size());
        assertEquals("chunk-0", dto.chunks().get(0).id());
        assertEquals(1, dto.chunks().get(0).chunkIndex());
        assertEquals("第一条 租赁标的。", dto.chunks().get(0).content());
        assertEquals("chunk-1", dto.chunks().get(1).id());
    }
}
