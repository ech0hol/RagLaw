package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.IngestStage;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.CategoryDocumentCountDto;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.dto.DocumentListPageDto;
import com.raglaw.rag.dto.DocumentUploadBatchResultDto;
import com.raglaw.rag.messaging.ParseMessagePublisher;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.search.ElasticsearchRetriever;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final CategoryService categoryService;
    private final DocumentStorageService documentStorageService;
    private final IngestService ingestService;
    private final ParseMessagePublisher parseMessagePublisher;
    private final RagProperties ragProperties;
    private final DocumentTitleLockService documentTitleLockService;
    private final ObjectProvider<ElasticsearchRetriever> elasticsearchRetriever;

    public DocumentUploadService(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            CategoryService categoryService,
            DocumentStorageService documentStorageService,
            IngestService ingestService,
            ParseMessagePublisher parseMessagePublisher,
            RagProperties ragProperties,
            DocumentTitleLockService documentTitleLockService,
            ObjectProvider<ElasticsearchRetriever> elasticsearchRetriever
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.categoryService = categoryService;
        this.documentStorageService = documentStorageService;
        this.ingestService = ingestService;
        this.parseMessagePublisher = parseMessagePublisher;
        this.ragProperties = ragProperties;
        this.documentTitleLockService = documentTitleLockService;
        this.elasticsearchRetriever = elasticsearchRetriever;
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

    @Transactional(readOnly = true)
    public DocumentListPageDto listByCategoryPath(
            String categoryPath,
            String docType,
            int page,
            int pageSize
    ) {
        int size = Math.min(Math.max(pageSize, 1), 50);
        int pageIndex = Math.max(page, 0);
        String normalizedPath = normalizeCategoryPath(categoryPath);
        boolean exactMatch = normalizedPath != null && categoryPathDepth(normalizedPath) >= 3;
        boolean excludeContract = docType == null || docType.isBlank();
        String resolvedDocType = docType == null || docType.isBlank() ? null : docType.trim();

        long total = documentRepository.countAdminList(
                normalizedPath,
                exactMatch,
                resolvedDocType,
                excludeContract
        );
        List<DocumentDto> items = documentRepository.findAdminList(
                normalizedPath,
                exactMatch,
                resolvedDocType,
                excludeContract,
                size,
                pageIndex * size
        ).stream().map(DocumentDto::from).toList();

        return new DocumentListPageDto(items, pageIndex, size, (int) total);
    }

    @Transactional(readOnly = true)
    public List<CategoryDocumentCountDto> categoryDocumentCounts() {
        return documentRepository.countDocumentsByCategory().stream()
                .map(row -> new CategoryDocumentCountDto(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue()
                ))
                .toList();
    }

    private static String normalizeCategoryPath(String categoryPath) {
        if (categoryPath == null || categoryPath.isBlank()) {
            return null;
        }
        return categoryPath.trim();
    }

    private static int categoryPathDepth(String categoryPath) {
        return (int) Arrays.stream(categoryPath.split("/"))
                .filter(segment -> !segment.isBlank())
                .count();
    }

    @Transactional
    public DocumentDto upload(MultipartFile file, String categoryId) {
        return upload(file, categoryId, false, 1);
    }

    @Transactional
    public DocumentDto upload(MultipartFile file, String categoryId, boolean deferIngest) {
        return upload(file, categoryId, deferIngest, 1);
    }

    @Transactional
    public DocumentUploadBatchResultDto uploadBatch(List<MultipartFile> files, String categoryId) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "上传文件不能为空");
        }
        CategoryEntity category = validateCategory(categoryId);
        UploadIngestMode mode = resolveIngestMode(category.getDocType(), files.size(), false);
        List<DocumentDto> items = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            items.add(uploadOne(file, category, mode, false));
        }
        return new DocumentUploadBatchResultDto(items, mode.name());
    }

    @Transactional
    public DocumentUploadBatchResultDto uploadContractsBatch(List<MultipartFile> files, String categoryId) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "上传文件不能为空");
        }
        CategoryEntity category = validateCategory(categoryId);
        UploadIngestMode mode = resolveIngestMode(category.getDocType(), files.size(), true);
        List<DocumentDto> items = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            items.add(uploadOne(file, category, mode, true));
        }
        return new DocumentUploadBatchResultDto(items, mode.name());
    }

    private DocumentDto upload(
            MultipartFile file,
            String categoryId,
            boolean deferIngest,
            int batchSize
    ) {
        CategoryEntity category = validateCategory(categoryId);
        UploadIngestMode mode = resolveIngestMode(category.getDocType(), batchSize, deferIngest);
        return uploadOne(file, category, mode, deferIngest);
    }

    private DocumentDto uploadOne(
            MultipartFile file,
            CategoryEntity category,
            UploadIngestMode mode,
            boolean deferIngest
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "上传文件不能为空");
        }
        String title = extractTitle(file);
        return documentTitleLockService.withTitleLock(title, () -> {
            DocumentEntity existing = findReplaceableDocument(title);
            if (existing != null) {
                return replaceDocument(existing, file, category, mode, deferIngest);
            }
            return createDocument(file, category, mode, deferIngest);
        });
    }

    private DocumentDto createDocument(
            MultipartFile file,
            CategoryEntity category,
            UploadIngestMode mode,
            boolean deferIngest
    ) {
        String documentId = Ids.newId();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.md";
        String storageKey = storeFile(documentId, file);
        String title = extractTitle(file);
        DocumentEntity document = new DocumentEntity(
                documentId,
                category.getId(),
                title,
                category.getDocType(),
                CurrentUserHolder.get(),
                storageKey
        );
        documentRepository.save(document);
        scheduleIngest(document, mode, deferIngest);
        return DocumentDto.from(documentRepository.findById(documentId).orElseThrow());
    }

    private DocumentDto replaceDocument(
            DocumentEntity document,
            MultipartFile file,
            CategoryEntity category,
            UploadIngestMode mode,
            boolean deferIngest
    ) {
        String documentId = document.getId();
        if (document.getMinioKey() != null && !document.getMinioKey().isBlank()) {
            documentStorageService.delete(document.getMinioKey());
        }
        documentChunkRepository.deleteByDocumentId(documentId);
        ElasticsearchRetriever retriever = elasticsearchRetriever.getIfAvailable();
        if (retriever != null && retriever.isEnabled()) {
            retriever.deleteByDocumentId(documentId);
        }

        String storageKey = storeFile(documentId, file);
        document.setCategoryId(category.getId());
        document.setTitle(extractTitle(file));
        document.setDocType(category.getDocType());
        document.setMinioKey(storageKey);
        document.setMetadataJson(null);
        document.setFullText(null);
        document.setIngestError(null);
        document.setRejectReason(null);
        document.bumpIndexVersion();
        documentRepository.save(document);
        scheduleIngest(document, mode, deferIngest);
        return DocumentDto.from(documentRepository.findById(documentId).orElseThrow());
    }

    private void scheduleIngest(DocumentEntity document, UploadIngestMode mode, boolean deferIngest) {
        if (deferIngest) {
            document.setIngestStage(IngestStage.PENDING);
            documentRepository.save(document);
            if (ragProperties.getRabbit().isEnabled()) {
                parseMessagePublisher.publishParseJob(document.getId());
            }
            return;
        }
        if (mode == UploadIngestMode.ASYNC && ragProperties.getRabbit().isEnabled()) {
            document.setIngestStage(IngestStage.PENDING);
            documentRepository.save(document);
            parseMessagePublisher.publishParseJob(document.getId());
            return;
        }
        ingestService.ingest(document);
    }

    private UploadIngestMode resolveIngestMode(String docType, int fileCount, boolean deferIngest) {
        if (deferIngest || "CONTRACT".equals(docType)) {
            return UploadIngestMode.ASYNC;
        }
        if (fileCount >= 2 && ragProperties.getRabbit().isEnabled()) {
            return UploadIngestMode.ASYNC;
        }
        return UploadIngestMode.SYNC;
    }

    private DocumentEntity findReplaceableDocument(String title) {
        List<DocumentEntity> matches = documentRepository.findByTitle(title);
        if (matches.isEmpty()) {
            return null;
        }
        return matches.stream()
                .filter(doc -> !"CONTRACT".equals(doc.getDocType()))
                .findFirst()
                .orElse(null);
    }

    private CategoryEntity validateCategory(String categoryId) {
        CategoryEntity category = categoryService.findEntity(categoryId);
        if (category.getLevel() != 3) {
            throw new BusinessException(ErrorCodes.VALIDATION, "文档必须上传到 L3 类目");
        }
        return category;
    }

    private String storeFile(String documentId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.md";
        try {
            return documentStorageService.store(
                    documentId,
                    originalFilename,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );
        } catch (IOException ex) {
            throw new BusinessException(ErrorCodes.INTERNAL, "读取上传文件失败");
        }
    }

    private static String extractTitle(MultipartFile file) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.md";
        return originalFilename.replaceFirst("\\.[^.]+$", "");
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
