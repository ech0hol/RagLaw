package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.common.web.HttpDownloadHeaders;
import com.raglaw.rag.dto.DocumentExcerptDto;
import com.raglaw.rag.dto.DocumentTextDto;
import com.raglaw.rag.dto.KnowledgeDocumentDto;
import com.raglaw.rag.dto.KnowledgeSearchPageDto;
import com.raglaw.rag.dto.KnowledgeStatsDto;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.DocumentExcerptService;
import com.raglaw.rag.service.DocumentFullTextService;
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
    private final DocumentFullTextService documentFullTextService;
    private final DocumentExcerptService documentExcerptService;
    private final IngestService ingestService;
    private final DocumentRepository documentRepository;

    public KnowledgeSearchController(
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeDocumentService knowledgeDocumentService,
            DocumentFullTextService documentFullTextService,
            DocumentExcerptService documentExcerptService,
            IngestService ingestService,
            DocumentRepository documentRepository
    ) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.documentFullTextService = documentFullTextService;
        this.documentExcerptService = documentExcerptService;
        this.ingestService = ingestService;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/stats")
    public ApiResponse<KnowledgeStatsDto> stats() {
        long caseCount = documentRepository.countByDocTypeAndStatus("CASE", DocStatus.INDEXED);
        long statuteCount = documentRepository.countByDocTypeAndStatus("STATUTE", DocStatus.INDEXED);
        return ApiResponse.ok(new KnowledgeStatsDto(caseCount, statuteCount));
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

    @GetMapping("/documents/{documentId}/text")
    public ApiResponse<DocumentTextDto> documentText(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(documentFullTextService.getText(documentId));
    }

    @GetMapping("/documents/{documentId}/excerpt")
    public ApiResponse<DocumentExcerptDto> documentExcerpt(
            @PathVariable("documentId") String documentId,
            @RequestParam("anchors") String anchors
    ) {
        return ApiResponse.ok(documentExcerptService.getExcerpt(documentId, anchors));
    }

    @GetMapping("/documents/{documentId}/download")
    public void download(
            @PathVariable("documentId") String documentId,
            HttpServletResponse response
    ) throws Exception {
        streamDocument(documentId, response, true);
    }

    @GetMapping("/documents/{documentId}/preview")
    public void preview(
            @PathVariable("documentId") String documentId,
            HttpServletResponse response
    ) throws Exception {
        streamDocument(documentId, response, false);
    }

    private void streamDocument(String documentId, HttpServletResponse response, boolean attachment) throws Exception {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        String filename = ingestService.resolveOriginalFilename(document);
        String contentType = resolveContentType(filename);
        response.setContentType(contentType);
        response.setHeader(
                "Content-Disposition",
                attachment ? HttpDownloadHeaders.attachmentFilename(filename) : HttpDownloadHeaders.inlineFilename(filename)
        );
        try (InputStream inputStream = ingestService.download(documentId)) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }
    }

    private static String resolveContentType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
