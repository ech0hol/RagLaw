package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.search.ElasticsearchRetriever;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private final DocumentRepository documentRepository;
    private final IngestPipeline ingestPipeline;
    private final ObjectProvider<ElasticsearchRetriever> elasticsearchRetriever;

    public ApprovalService(
            DocumentRepository documentRepository,
            IngestPipeline ingestPipeline,
            ObjectProvider<ElasticsearchRetriever> elasticsearchRetriever
    ) {
        this.documentRepository = documentRepository;
        this.ingestPipeline = ingestPipeline;
        this.elasticsearchRetriever = elasticsearchRetriever;
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
        ingestPipeline.index(document);
        return DocumentDto.from(documentRepository.findById(documentId).orElseThrow());
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

        ElasticsearchRetriever retriever = elasticsearchRetriever.getIfAvailable();
        if (retriever != null && retriever.isEnabled()) {
            retriever.deleteByDocumentId(documentId);
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
