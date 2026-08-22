package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.messaging.ParseMessagePublisher;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.IOException;
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

    public DocumentUploadService(
            DocumentRepository documentRepository,
            CategoryService categoryService,
            DocumentStorageService documentStorageService,
            IngestService ingestService,
            ParseMessagePublisher parseMessagePublisher
    ) {
        this.documentRepository = documentRepository;
        this.categoryService = categoryService;
        this.documentStorageService = documentStorageService;
        this.ingestService = ingestService;
        this.parseMessagePublisher = parseMessagePublisher;
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
        parseMessagePublisher.publishParseJob(documentId);
        return DocumentDto.from(document);
    }

    @Transactional
    public DocumentDto ingestNow(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        try {
            ingestService.ingest(document);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodes.INTERNAL, "文档入库失败");
        }
        return DocumentDto.from(documentRepository.findById(documentId).orElseThrow());
    }
}
