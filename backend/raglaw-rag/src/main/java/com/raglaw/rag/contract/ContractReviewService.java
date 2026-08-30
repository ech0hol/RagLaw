package com.raglaw.rag.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.domain.IngestStage;
import com.raglaw.rag.dto.ContractReviewDto;
import com.raglaw.rag.dto.ContractRiskDto;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IngestService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractReviewService {

    private final ContractAccessService contractAccessService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ContractRiskRepository riskRepository;
    private final ContractRiskAnalyzer riskAnalyzer;
    private final IngestService ingestService;
    private final ObjectMapper objectMapper;

    public ContractReviewService(
            ContractAccessService contractAccessService,
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            ContractRiskRepository riskRepository,
            ContractRiskAnalyzer riskAnalyzer,
            IngestService ingestService,
            ObjectMapper objectMapper
    ) {
        this.contractAccessService = contractAccessService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.riskRepository = riskRepository;
        this.riskAnalyzer = riskAnalyzer;
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ContractRiskDto> listRisks(String documentId) {
        ensureContractDocument(documentId);
        return riskRepository.findByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .map(ContractRiskDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractReviewDto getReview(String documentId) {
        DocumentEntity document = ensureContractDocument(documentId);
        var risks = riskRepository.findByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .map(ContractRiskDto::from)
                .toList();
        return toReviewDto(document, risks);
    }

    @Transactional
    public ContractReviewDto review(String documentId) {
        DocumentEntity document = ensureContractDocument(documentId);
        var risks = riskAnalyzer.analyze(documentId).stream()
                .map(ContractRiskDto::from)
                .toList();
        return toReviewDto(document, risks);
    }

    @Transactional
    public ContractReviewDto ingestAndReview(String documentId) {
        DocumentEntity document = ensureContractDocument(documentId);
        ingestService.ingest(document);
        document = documentRepository.findById(documentId).orElseThrow();
        var risks = riskRepository.findByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .map(ContractRiskDto::from)
                .toList();
        return toReviewDto(document, risks);
    }

    @Transactional
    public ContractReviewDto parseOnly(String documentId) {
        DocumentEntity document = ensureContractDocument(documentId);
        ingestService.parse(document);
        document = documentRepository.findById(documentId).orElseThrow();
        return toReviewDto(document, List.of());
    }

    @Transactional
    public void acceptRisk(String documentId, String riskId) {
        ensureContractDocument(documentId);
        ContractRiskEntity risk = findRisk(documentId, riskId);
        applyAcceptance(risk);
        riskRepository.save(risk);
    }

    @Transactional
    public void unacceptRisk(String documentId, String riskId) {
        ensureContractDocument(documentId);
        ContractRiskEntity risk = findRisk(documentId, riskId);
        risk.setAccepted(false);
        risk.setRevisedExcerpt(null);
        riskRepository.save(risk);
    }

    @Transactional
    public void acceptAllRisks(String documentId) {
        ensureContractDocument(documentId);
        for (ContractRiskEntity risk : riskRepository.findByDocumentIdOrderByCreatedAtAsc(documentId)) {
            applyAcceptance(risk);
            riskRepository.save(risk);
        }
    }

    private void applyAcceptance(ContractRiskEntity risk) {
        String chunkText = chunkRepository.findById(risk.getChunkId())
                .map(DocumentChunkEntity::getContent)
                .orElse("");
        risk.setAccepted(true);
        risk.setRevisedExcerpt(RevisionCalculator.compute(chunkText, risk.getExcerpt(), risk.getSuggestion()));
    }

    private ContractRiskEntity findRisk(String documentId, String riskId) {
        ContractRiskEntity risk = riskRepository.findById(riskId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "风险项不存在"));
        if (!documentId.equals(risk.getDocumentId())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "风险项与文档不匹配");
        }
        return risk;
    }

    private ContractReviewDto toReviewDto(DocumentEntity document, List<ContractRiskDto> risks) {
        String suggestedAgentCode = "CONTRACT";
        String extractMethod = "text";
        boolean ocrUsed = false;
        String analysisModel = null;
        int ragHitCount = 0;
        String reviewStatus = ContractReviewStatus.NOT_RUN;
        String reviewError = null;
        if (document.getMetadataJson() != null) {
            try {
                var node = objectMapper.readTree(document.getMetadataJson());
                suggestedAgentCode = node.path("suggestedAgentCode").asText(suggestedAgentCode);
                extractMethod = node.path("extractMethod").asText(extractMethod);
                ocrUsed = node.path("ocrUsed").asBoolean(false);
                String model = node.path("contractAnalysisModel").asText("");
                analysisModel = model.isBlank() ? null : model;
                ragHitCount = node.path("contractRagHitCount").asInt(0);
                String status = node.path("contractReviewStatus").asText("");
                reviewStatus = status.isBlank() ? ContractReviewStatus.NOT_RUN : status;
                String error = node.path("contractReviewError").asText("");
                reviewError = error.isBlank() ? null : error;
            } catch (Exception ignored) {
                // keep defaults
            }
        }
        if (!ContractReviewStatus.PARTIAL.equals(reviewStatus)) {
            if (!risks.isEmpty()
                    && (ContractReviewStatus.NOT_RUN.equals(reviewStatus)
                    || ContractReviewStatus.RUNNING.equals(reviewStatus))) {
                reviewStatus = ContractReviewStatus.COMPLETED;
            } else if (ContractReviewStatus.NOT_RUN.equals(reviewStatus) && analysisModel != null) {
                reviewStatus = ContractReviewStatus.COMPLETED;
            }
        }
        String ingestStage = document.getIngestStage();
        if ((ingestStage == null || ingestStage.isBlank()) && !risks.isEmpty()) {
            ingestStage = IngestStage.INDEXED;
        }
        return new ContractReviewDto(
                document.getId(),
                suggestedAgentCode,
                extractMethod,
                ocrUsed,
                analysisModel,
                ragHitCount,
                reviewStatus,
                reviewError,
                ingestStage,
                risks
        );
    }

    private DocumentEntity ensureContractDocument(String documentId) {
        return contractAccessService.requireOwnedContract(documentId);
    }
}
