package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentDeletionService {

    private static final Set<String> KNOWLEDGE_DOC_TYPES = Set.of("STATUTE", "CASE");

    private final DocumentRepository documentRepository;
    private final ContractRiskRepository riskRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentStorageService documentStorageService;
    private final IndexOutboxService indexOutboxService;

    public DocumentDeletionService(
            DocumentRepository documentRepository,
            ContractRiskRepository riskRepository,
            DocumentChunkRepository chunkRepository,
            DocumentStorageService documentStorageService,
            IndexOutboxService indexOutboxService
    ) {
        this.documentRepository = documentRepository;
        this.riskRepository = riskRepository;
        this.chunkRepository = chunkRepository;
        this.documentStorageService = documentStorageService;
        this.indexOutboxService = indexOutboxService;
    }

    @Transactional
    public void deleteDocument(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        deleteEntity(document);
    }

    @Transactional
    public int deleteAllByDocTypes(List<String> docTypes) {
        if (docTypes == null || docTypes.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "docType 不能为空");
        }
        for (String docType : docTypes) {
            if (!KNOWLEDGE_DOC_TYPES.contains(docType)) {
                throw new BusinessException(ErrorCodes.VALIDATION, "仅支持删除法规或案例文档: " + docType);
            }
        }
        List<DocumentEntity> documents = new ArrayList<>();
        for (String docType : docTypes) {
            documents.addAll(documentRepository.findByDocType(docType));
        }
        for (DocumentEntity document : documents) {
            deleteEntity(document);
        }
        return documents.size();
    }

    private void deleteEntity(DocumentEntity document) {
        String documentId = document.getId();
        indexOutboxService.enqueueDeleteDocument(documentId, document.getIndexVersion());
        indexOutboxService.flushPendingForDocument(documentId);
        if ("CONTRACT".equals(document.getDocType())) {
            riskRepository.deleteByDocumentId(documentId);
        }
        chunkRepository.deleteByDocumentId(documentId);
        if (document.getMinioKey() != null && !document.getMinioKey().isBlank()) {
            documentStorageService.delete(document.getMinioKey());
        }
        documentRepository.delete(document);
    }
}
