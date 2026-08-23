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
import java.util.Map;
import java.util.stream.Collectors;
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
        return buildFullText(documentId);
    }

    String buildFullText(String documentId) {
        List<DocumentChunkEntity> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<ContractRiskEntity> risks = riskRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        Map<String, List<ContractRiskEntity>> acceptedByChunk = risks.stream()
                .filter(ContractRiskEntity::isAccepted)
                .collect(Collectors.groupingBy(ContractRiskEntity::getChunkId));

        return chunks.stream()
                .map(chunk -> resolveChunkContent(chunk, acceptedByChunk.get(chunk.getId())))
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private String resolveChunkContent(DocumentChunkEntity chunk, List<ContractRiskEntity> acceptedRisks) {
        if (acceptedRisks == null || acceptedRisks.isEmpty()) {
            return chunk.getContent();
        }
        String content = chunk.getContent();
        for (ContractRiskEntity risk : acceptedRisks) {
            if (risk.getRevisedExcerpt() != null && !risk.getRevisedExcerpt().isBlank()) {
                content = risk.getRevisedExcerpt();
            } else {
                content = RevisionCalculator.compute(content, risk.getExcerpt(), risk.getSuggestion());
            }
        }
        return content;
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
