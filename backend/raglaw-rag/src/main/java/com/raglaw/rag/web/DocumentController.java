package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.service.DocumentUploadService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/documents")
public class DocumentController {

    private final DocumentUploadService documentUploadService;

    public DocumentController(DocumentUploadService documentUploadService) {
        this.documentUploadService = documentUploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categoryId") String categoryId
    ) {
        return ApiResponse.ok(documentUploadService.upload(file, categoryId));
    }

    @PostMapping("/{documentId}/ingest")
    public ApiResponse<DocumentDto> ingest(@PathVariable String documentId) {
        return ApiResponse.ok(documentUploadService.ingestNow(documentId));
    }
}
