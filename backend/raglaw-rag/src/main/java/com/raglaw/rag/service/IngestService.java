package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.InputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final IngestPipeline ingestPipeline;

    public IngestService(
            DocumentRepository documentRepository,
            DocumentStorageService documentStorageService,
            IngestPipeline ingestPipeline
    ) {
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.ingestPipeline = ingestPipeline;
    }

    @Transactional
    public void ingest(DocumentEntity document) {
        ingestPipeline.sync(document);
    }

    @Transactional
    public void parse(DocumentEntity document) {
        ingestPipeline.parse(document);
    }

    public InputStream download(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        return documentStorageService.load(document.getMinioKey());
    }

    public String resolveOriginalFilename(DocumentEntity document) {
        String key = document.getMinioKey();
        if (key == null || key.isBlank()) {
            return document.getTitle() + ".md";
        }
        int slash = Math.max(key.lastIndexOf('/'), key.lastIndexOf('\\'));
        return slash >= 0 ? key.substring(slash + 1) : key;
    }
}
