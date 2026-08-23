package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.IngestStage;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.messaging.ParseMessagePublisher;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    private final DocumentRepository documentRepository;
    private final CategoryService categoryService;
    private final DocumentStorageService documentStorageService;
    private final IngestService ingestService;
    private final ParseMessagePublisher parseMessagePublisher;
    private final RagProperties ragProperties;

    public DocumentUploadService(
            DocumentRepository documentRepository,
            CategoryService categoryService,
            DocumentStorageService documentStorageService,
            IngestService ingestService,
            ParseMessagePublisher parseMessagePublisher,
            RagProperties ragProperties
    ) {
        this.documentRepository = documentRepository;
        this.categoryService = categoryService;
        this.documentStorageService = documentStorageService;
        this.ingestService = ingestService;
        this.parseMessagePublisher = parseMessagePublisher;
        this.ragProperties = ragProperties;
    }

    @Transactional(readOnly = true)
    public DocumentDto getDocument(String documentId) {
        return DocumentDto.from(findDocument(documentId));
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> listRecent(int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        return documentRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .limit(size)
                .map(DocumentDto::from)
                .toList();
    }

    @Transactional
    public DocumentDto upload(MultipartFile file, String categoryId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "上传文件不能为空");
        }
        CategoryEntity category = categoryService.findEntity(categoryId);
        if (category.getLevel() != 3) {
            throw new BusinessException(ErrorCodes.VALIDATION, "文档必须上传到 L3 类目");
        }

        String documentId = Ids.newId();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.md";
        String storageKey;
        try {
            storageKey = documentStorageService.store(
                    documentId,
                    originalFilename,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );
        } catch (IOException ex) {
            throw new BusinessException(ErrorCodes.INTERNAL, "读取上传文件失败");
        }

        String title = originalFilename.replaceFirst("\\.[^.]+$", "");
        DocumentEntity document = new DocumentEntity(
                documentId,
                categoryId,
                title,
                category.getDocType(),
                CurrentUserHolder.get(),
                storageKey
        );
        documentRepository.save(document);
        if (ragProperties.getRabbit().isEnabled()) {
            document.setIngestStage(IngestStage.PENDING);
            documentRepository.save(document);
            parseMessagePublisher.publishParseJob(documentId);
        } else {
            ingestService.ingest(document);
        }
        return DocumentDto.from(documentRepository.findById(documentId).orElseThrow());
    }

    @Transactional
    public DocumentDto ingestNow(String documentId) {
        DocumentEntity document = findDocument(documentId);
        try {
            ingestService.ingest(document);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodes.INTERNAL, "文档入库失败");
        }
        return DocumentDto.from(documentRepository.findById(documentId).orElseThrow());
    }

    @Transactional
    public DocumentDto retryIngest(String documentId) {
        DocumentEntity document = findDocument(documentId);
        if (!IngestStage.FAILED.equals(document.getIngestStage())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "仅失败状态的文档可重试入库");
        }
        document.setIngestStage(IngestStage.PENDING);
        document.setIngestError(null);
        documentRepository.save(document);
        if (ragProperties.getRabbit().isEnabled()) {
            parseMessagePublisher.publishParseJob(documentId);
        } else {
            ingestService.ingest(document);
        }
        return DocumentDto.from(documentRepository.findById(documentId).orElseThrow());
    }

    private DocumentEntity findDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
    }
}
