package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private final DocumentRepository documentRepository;
    private final IngestService ingestService;
    private final ObjectProvider<VectorStoreService> vectorStoreService;

    public ApprovalService(
            DocumentRepository documentRepository,
            IngestService ingestService,
            ObjectProvider<VectorStoreService> vectorStoreService
    ) {
        this.documentRepository = documentRepository;
        this.ingestService = ingestService;
        this.vectorStoreService = vectorStoreService;
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> listPending() {
        return documentRepository.findByStatusOrderByCreatedAtDesc(DocStatus.AWAITING_APPROVAL).stream()
                .map(DocumentDto::from)
                .toList();
    }

    @Transactional
    public DocumentDto approve(String documentId) {
        DocumentEntity document = findAwaiting(documentId);
        document.setStatus(DocStatus.INDEXED);
        document.setRejectReason(null);
        documentRepository.save(document);
        return DocumentDto.from(document);
    }

    @Transactional
    public DocumentDto reject(String documentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "驳回原因不能为空");
        }
        DocumentEntity document = findAwaiting(documentId);
        document.setStatus(DocStatus.REJECTED);
        document.setRejectReason(reason);
        documentRepository.save(document);

        VectorStoreService vectorStore = vectorStoreService.getIfAvailable();
        if (vectorStore != null && vectorStore.isEnabled()) {
            vectorStore.deleteByDocumentId(documentId);
        }
        return DocumentDto.from(document);
    }

    private DocumentEntity findAwaiting(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        if (document.getStatus() != DocStatus.AWAITING_APPROVAL) {
            throw new BusinessException(ErrorCodes.VALIDATION, "文档不在待审批状态");
        }
        return document;
    }
}
