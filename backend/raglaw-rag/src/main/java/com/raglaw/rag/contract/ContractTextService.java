package com.raglaw.rag.contract;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.ContractTextDto;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IngestService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractTextService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ContractRiskRepository riskRepository;
    private final IngestService ingestService;

    public ContractTextService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            ContractRiskRepository riskRepository,
            IngestService ingestService
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.riskRepository = riskRepository;
        this.ingestService = ingestService;
    }

    @Transactional(readOnly = true)
    public ContractTextDto getText(String documentId) {
        DocumentEntity document = ensureContract(documentId);
        String filename = ingestService.resolveOriginalFilename(document);
        boolean pdf = filename.toLowerCase().endsWith(".pdf");
        String content = buildFullText(documentId);
        return new ContractTextDto(documentId, filename, content, pdf);
    }

    @Transactional(readOnly = true)
    public String buildRevisedText(String documentId) {
        ensureContract(documentId);
        StringBuilder sb = new StringBuilder(buildFullText(documentId));
        List<ContractRiskEntity> risks = riskRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        if (!risks.isEmpty()) {
            sb.append("\n\n---\n\n## 修订建议（已采纳）\n\n");
            int index = 1;
            for (ContractRiskEntity risk : risks) {
                sb.append("### ").append(index++).append(". ").append(risk.getSummary()).append("\n\n");
                if (risk.getExcerpt() != null && !risk.getExcerpt().isBlank()) {
                    sb.append("**原文摘录：** ").append(risk.getExcerpt()).append("\n\n");
                }
                if (risk.getSuggestion() != null && !risk.getSuggestion().isBlank()) {
                    sb.append("**修订建议：** ").append(risk.getSuggestion()).append("\n\n");
                }
            }
        }
        return sb.toString();
    }

    private String buildFullText(String documentId) {
        return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(DocumentChunkEntity::getContent)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private DocumentEntity ensureContract(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        if (!"CONTRACT".equals(document.getDocType())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "仅支持合同文档");
        }
        return document;
    }
}
