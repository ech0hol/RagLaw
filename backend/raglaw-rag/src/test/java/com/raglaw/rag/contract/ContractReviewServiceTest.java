package com.raglaw.rag.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.domain.IngestStage;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IngestService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractReviewServiceTest {

    @Mock
    private ContractAccessService contractAccessService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private ContractRiskRepository riskRepository;

    @Mock
    private ContractRiskAnalyzer riskAnalyzer;

    @Mock
    private IngestService ingestService;

    private ContractReviewService service;

    @BeforeEach
    void setUp() {
        service = new ContractReviewService(
                contractAccessService,
                documentRepository,
                chunkRepository,
                riskRepository,
                riskAnalyzer,
                ingestService,
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    void getReview_infersCompletedWhenRisksExistButMetadataSaysNotRun() {
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "合同", "CONTRACT", "user", "key.jpg");
        document.setMetadataJson("{\"contractReviewStatus\":\"NOT_RUN\"}");
        ContractRiskEntity risk = new ContractRiskEntity(
                "risk-1",
                "doc-1",
                "chunk-1",
                "HIGH",
                "付款",
                "付款周期过长",
                "甲方应在30日内付款",
                "缩短付款周期"
        );

        when(contractAccessService.requireOwnedContract("doc-1")).thenReturn(document);
        when(riskRepository.findByDocumentIdOrderByCreatedAtAsc("doc-1")).thenReturn(List.of(risk));

        var review = service.getReview("doc-1");

        assertThat(review.reviewStatus()).isEqualTo(ContractReviewStatus.COMPLETED);
        assertThat(review.ingestStage()).isEqualTo(IngestStage.INDEXED);
        assertThat(review.risks()).hasSize(1);
    }

    @Test
    void getReview_keepsNotRunWhenNoRisksPersisted() {
        DocumentEntity document = new DocumentEntity("doc-2", "cat", "合同", "CONTRACT", "user", "key.jpg");
        document.setMetadataJson("{\"contractReviewStatus\":\"NOT_RUN\"}");

        when(contractAccessService.requireOwnedContract("doc-2")).thenReturn(document);
        when(riskRepository.findByDocumentIdOrderByCreatedAtAsc("doc-2")).thenReturn(List.of());

        var review = service.getReview("doc-2");

        assertThat(review.reviewStatus()).isEqualTo(ContractReviewStatus.NOT_RUN);
    }

    @Test
    void getReview_infersCompletedWhenRunningButRisksExist() {
        DocumentEntity document = new DocumentEntity("doc-3", "cat", "合同", "CONTRACT", "user", "key.pdf");
        document.setMetadataJson("{\"contractReviewStatus\":\"RUNNING\"}");
        ContractRiskEntity risk = new ContractRiskEntity(
                "risk-2",
                "doc-3",
                "chunk-1",
                "MEDIUM",
                "违约",
                "违约金过高",
                "违约金十万元",
                "调低违约金"
        );

        when(contractAccessService.requireOwnedContract("doc-3")).thenReturn(document);
        when(riskRepository.findByDocumentIdOrderByCreatedAtAsc("doc-3")).thenReturn(List.of(risk));

        var review = service.getReview("doc-3");

        assertThat(review.reviewStatus()).isEqualTo(ContractReviewStatus.COMPLETED);
        assertThat(review.ingestStage()).isEqualTo(IngestStage.INDEXED);
    }

    @Test
    void getReview_rejectsNonOwner() {
        CurrentUserHolder.set("user-b");
        when(contractAccessService.requireOwnedContract("doc-4"))
                .thenThrow(new BusinessException(ErrorCodes.FORBIDDEN, "无权访问该合同"));

        assertThatThrownBy(() -> service.getReview("doc-4"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCodes.FORBIDDEN);
    }
}
