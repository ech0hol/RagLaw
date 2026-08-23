package com.raglaw.rag.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.ContractReviewDto;
import com.raglaw.rag.dto.ContractRiskDto;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IngestService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractReviewService {

    private final DocumentRepository documentRepository;
    private final ContractRiskRepository riskRepository;
    private final ContractRiskAnalyzer riskAnalyzer;
    private final IngestService ingestService;
    private final ObjectMapper objectMapper;

    public ContractReviewService(
            DocumentRepository documentRepository,
            ContractRiskRepository riskRepository,
            ContractRiskAnalyzer riskAnalyzer,
            IngestService ingestService,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
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
    public void acceptRisk(String documentId, String riskId) {
        ensureContractDocument(documentId);
        ContractRiskEntity risk = riskRepository.findById(riskId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "风险项不存在"));
        if (!documentId.equals(risk.getDocumentId())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "风险项与文档不匹配");
        }
        risk.setAccepted(true);
        riskRepository.save(risk);
    }

    @Transactional
    public void acceptAllRisks(String documentId) {
        ensureContractDocument(documentId);
        for (ContractRiskEntity risk : riskRepository.findByDocumentIdOrderByCreatedAtAsc(documentId)) {
            risk.setAccepted(true);
            riskRepository.save(risk);
        }
    }

    private ContractReviewDto toReviewDto(DocumentEntity document, List<ContractRiskDto> risks) {
        String suggestedAgentCode = "CONTRACT_GENERAL";
        String extractMethod = "text";
        boolean ocrUsed = false;
        if (document.getMetadataJson() != null) {
            try {
                var node = objectMapper.readTree(document.getMetadataJson());
                suggestedAgentCode = node.path("suggestedAgentCode").asText(suggestedAgentCode);
                extractMethod = node.path("extractMethod").asText(extractMethod);
                ocrUsed = node.path("ocrUsed").asBoolean(false);
            } catch (Exception ignored) {
                // keep defaults
            }
        }
        return new ContractReviewDto(
                document.getId(),
                suggestedAgentCode,
                extractMethod,
                ocrUsed,
                risks
        );
    }

    private DocumentEntity ensureContractDocument(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        if (!"CONTRACT".equals(document.getDocType())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "仅支持合同文档审查");
        }
        return document;
    }
}
