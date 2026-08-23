package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.KnowledgeDocumentDto;
import com.raglaw.rag.dto.KnowledgeSearchPageDto;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IngestService;
import com.raglaw.rag.service.KnowledgeDocumentService;
import com.raglaw.rag.service.KnowledgeSearchService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeSearchController {

    private final KnowledgeSearchService knowledgeSearchService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final IngestService ingestService;
    private final DocumentRepository documentRepository;

    public KnowledgeSearchController(
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeDocumentService knowledgeDocumentService,
            IngestService ingestService,
            DocumentRepository documentRepository
    ) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.ingestService = ingestService;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/search")
    public ApiResponse<KnowledgeSearchPageDto> search(
            @RequestParam("q") String query,
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "l2Path", required = false) String l2Path,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    ) {
        int size = Math.min(Math.max(pageSize, 1), 20);
        return ApiResponse.ok(knowledgeSearchService.searchPage(query, docType, l2Path, page, size));
    }

    @GetMapping("/documents/{documentId}")
    public ApiResponse<KnowledgeDocumentDto> document(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(knowledgeDocumentService.getDocument(documentId));
    }

    @GetMapping("/documents/{documentId}/download")
    public void download(
            @PathVariable("documentId") String documentId,
            HttpServletResponse response
    ) throws Exception {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        String filename = ingestService.resolveOriginalFilename(document);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        try (InputStream inputStream = ingestService.download(documentId)) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }
    }
}
