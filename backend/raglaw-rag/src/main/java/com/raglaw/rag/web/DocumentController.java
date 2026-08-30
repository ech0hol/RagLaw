package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.CategoryDocumentCountDto;
import com.raglaw.rag.dto.DocumentDeleteResultDto;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.dto.DocumentListPageDto;
import com.raglaw.rag.dto.DocumentReindexBatchResultDto;
import com.raglaw.rag.dto.DocumentUploadBatchResultDto;
import com.raglaw.rag.dto.ReindexSelectedRequest;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.DocumentDeletionService;
import com.raglaw.rag.service.DocumentReindexService;
import com.raglaw.rag.service.DocumentUploadService;
import com.raglaw.rag.service.IngestPipeline;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/documents")
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final DocumentDeletionService documentDeletionService;
    private final DocumentReindexService documentReindexService;
    private final DocumentRepository documentRepository;
    private final IngestPipeline ingestPipeline;

    public DocumentController(
            DocumentUploadService documentUploadService,
            DocumentDeletionService documentDeletionService,
            DocumentReindexService documentReindexService,
            DocumentRepository documentRepository,
            IngestPipeline ingestPipeline
    ) {
        this.documentUploadService = documentUploadService;
        this.documentDeletionService = documentDeletionService;
        this.documentReindexService = documentReindexService;
        this.documentRepository = documentRepository;
        this.ingestPipeline = ingestPipeline;
    }

    @GetMapping("/recent")
    public ApiResponse<List<DocumentDto>> recent(
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(documentUploadService.listRecent(limit));
    }

    @GetMapping
    public ApiResponse<DocumentListPageDto> list(
            @RequestParam(value = "categoryPath", required = false) String categoryPath,
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(documentUploadService.listByCategoryPath(categoryPath, docType, page, pageSize));
    }

    @GetMapping("/category-counts")
    public ApiResponse<List<CategoryDocumentCountDto>> categoryCounts() {
        return ApiResponse.ok(documentUploadService.categoryDocumentCounts());
    }

    @DeleteMapping
    public ApiResponse<DocumentDeleteResultDto> deleteByDocTypes(
            @RequestParam("docType") String docType
    ) {
        List<String> docTypes = Arrays.stream(docType.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        int deleted = documentDeletionService.deleteAllByDocTypes(docTypes);
        return ApiResponse.ok(new DocumentDeleteResultDto(deleted));
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentDto> get(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(documentUploadService.getDocument(documentId));
    }

    @GetMapping("/{documentId}/embeddable-chunk-count")
    public ApiResponse<Integer> embeddableChunkCount(@PathVariable("documentId") String documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在");
        }
        return ApiResponse.ok(ingestPipeline.countEmbeddableChunks(documentId));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(@PathVariable("documentId") String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        if ("CONTRACT".equals(document.getDocType())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "合同文档请通过合同审查页删除");
        }
        documentDeletionService.deleteDocument(documentId);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categoryId") String categoryId
    ) {
        return ApiResponse.ok(documentUploadService.upload(file, categoryId));
    }

    @PostMapping(value = "/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentUploadBatchResultDto> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("categoryId") String categoryId
    ) {
        return ApiResponse.ok(documentUploadService.uploadBatch(files, categoryId));
    }

    @PostMapping("/{documentId}/ingest")
    public ApiResponse<DocumentDto> ingest(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(documentUploadService.ingestNow(documentId));
    }

    @PostMapping("/{documentId}/retry-ingest")
    public ApiResponse<DocumentDto> retryIngest(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(documentUploadService.retryIngest(documentId));
    }

    @PostMapping("/{documentId}/reindex")
    public ApiResponse<DocumentDto> reindex(@PathVariable("documentId") String documentId) {
        documentReindexService.reindexOne(documentId);
        return ApiResponse.ok(documentUploadService.getDocument(documentId));
    }

    @PostMapping("/reindex-batch")
    public ApiResponse<DocumentReindexBatchResultDto> reindexBatch(
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        int capped = Math.min(Math.max(limit, 1), 200);
        return ApiResponse.ok(documentReindexService.reindexBatch(docType, capped));
    }

    @PostMapping("/reindex-selected")
    public ApiResponse<DocumentReindexBatchResultDto> reindexSelected(
            @Valid @RequestBody ReindexSelectedRequest request
    ) {
        return ApiResponse.ok(documentReindexService.reindexByIds(request.documentIds()));
    }
}
