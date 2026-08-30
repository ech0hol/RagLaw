package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.DocumentReindexBatchResultDto;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentReindexService {

    private static final Logger log = LoggerFactory.getLogger(DocumentReindexService.class);
    private static final int MAX_SELECTED = 50;

    private final DocumentRepository documentRepository;
    private final IngestService ingestService;

    public DocumentReindexService(DocumentRepository documentRepository, IngestService ingestService) {
        this.documentRepository = documentRepository;
        this.ingestService = ingestService;
    }

    @Transactional
    public void reindexOne(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        ingestService.ingest(document);
    }

    public DocumentReindexBatchResultDto reindexBatch(String docType, int limit) {
        List<DocumentEntity> candidates = documentRepository.findByStatusOrderByCreatedAtDesc(DocStatus.INDEXED);
        List<DocumentEntity> filtered = new ArrayList<>();
        for (DocumentEntity document : candidates) {
            if (docType != null && !docType.isBlank() && !document.getDocType().equalsIgnoreCase(docType.trim())) {
                continue;
            }
            filtered.add(document);
            if (filtered.size() >= limit) {
                break;
            }
        }

        int succeeded = 0;
        List<DocumentReindexBatchResultDto.DocumentReindexFailureDto> failures = new ArrayList<>();
        for (DocumentEntity document : filtered) {
            try {
                ingestService.ingest(document);
                succeeded++;
            } catch (Exception e) {
                log.warn("Reindex failed for document {}: {}", document.getId(), e.getMessage());
                failures.add(new DocumentReindexBatchResultDto.DocumentReindexFailureDto(
                        document.getId(),
                        e.getMessage() == null ? "unknown error" : e.getMessage()
                ));
            }
        }
        return new DocumentReindexBatchResultDto(filtered.size(), succeeded, failures.size(), failures);
    }

    public DocumentReindexBatchResultDto reindexByIds(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "请至少选择一篇文档");
        }
        Set<String> uniqueIds = new LinkedHashSet<>(documentIds);
        if (uniqueIds.size() > MAX_SELECTED) {
            throw new BusinessException(ErrorCodes.VALIDATION, "最多选择 " + MAX_SELECTED + " 篇文档");
        }

        List<DocumentEntity> documents = new ArrayList<>();
        for (String documentId : uniqueIds) {
            DocumentEntity document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在: " + documentId));
            if ("CONTRACT".equals(document.getDocType())) {
                throw new BusinessException(ErrorCodes.VALIDATION, "合同文档不支持重建索引");
            }
            documents.add(document);
        }

        int succeeded = 0;
        List<DocumentReindexBatchResultDto.DocumentReindexFailureDto> failures = new ArrayList<>();
        for (DocumentEntity document : documents) {
            try {
                ingestService.ingest(document);
                succeeded++;
            } catch (Exception e) {
                log.warn("Reindex failed for document {}: {}", document.getId(), e.getMessage());
                failures.add(new DocumentReindexBatchResultDto.DocumentReindexFailureDto(
                        document.getId(),
                        e.getMessage() == null ? "unknown error" : e.getMessage()
                ));
            }
        }
        return new DocumentReindexBatchResultDto(documents.size(), succeeded, failures.size(), failures);
    }
}
