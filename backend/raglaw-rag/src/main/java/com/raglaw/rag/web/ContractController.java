package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.common.web.HttpDownloadHeaders;
import com.raglaw.rag.contract.ContractDocumentService;
import com.raglaw.rag.contract.ContractExportService;
import com.raglaw.rag.contract.ContractReviewService;
import com.raglaw.rag.contract.ContractTextService;
import com.raglaw.rag.dto.ContractReviewDto;
import com.raglaw.rag.dto.ContractRiskDto;
import com.raglaw.rag.dto.ContractSummaryDto;
import com.raglaw.rag.dto.ContractTextDto;
import com.raglaw.rag.dto.DocumentUploadBatchResultDto;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.service.DocumentUploadService;
import com.raglaw.rag.service.IngestService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController {

    private static final String DEFAULT_CONTRACT_CATEGORY = "cat_l3_contract_civil_general";

    private final ContractReviewService contractReviewService;
    private final ContractTextService contractTextService;
    private final ContractExportService contractExportService;
    private final ContractDocumentService contractDocumentService;
    private final DocumentUploadService documentUploadService;
    private final IngestService ingestService;

    public ContractController(
            ContractReviewService contractReviewService,
            ContractTextService contractTextService,
            ContractExportService contractExportService,
            ContractDocumentService contractDocumentService,
            DocumentUploadService documentUploadService,
            IngestService ingestService
    ) {
        this.contractReviewService = contractReviewService;
        this.contractTextService = contractTextService;
        this.contractExportService = contractExportService;
        this.contractDocumentService = contractDocumentService;
        this.documentUploadService = documentUploadService;
        this.ingestService = ingestService;
    }

    @GetMapping
    public ApiResponse<List<ContractSummaryDto>> list() {
        return ApiResponse.ok(contractDocumentService.listForCurrentUser());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "categoryId", required = false) String categoryId
    ) {
        String resolvedCategory = categoryId != null && !categoryId.isBlank()
                ? categoryId
                : DEFAULT_CONTRACT_CATEGORY;
        return ApiResponse.ok(documentUploadService.upload(file, resolvedCategory, true));
    }

    @PostMapping(value = "/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentUploadBatchResultDto> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "categoryId", required = false) String categoryId
    ) {
        String resolvedCategory = categoryId != null && !categoryId.isBlank()
                ? categoryId
                : DEFAULT_CONTRACT_CATEGORY;
        return ApiResponse.ok(documentUploadService.uploadContractsBatch(files, resolvedCategory));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(@PathVariable("documentId") String documentId) {
        contractDocumentService.deleteForCurrentUser(documentId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{documentId}/risks")
    public ApiResponse<List<ContractRiskDto>> risks(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(contractReviewService.listRisks(documentId));
    }

    @GetMapping("/{documentId}/text")
    public ApiResponse<ContractTextDto> text(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(contractTextService.getText(documentId));
    }

    @GetMapping("/{documentId}/file")
    public void file(
            @PathVariable("documentId") String documentId,
            HttpServletResponse response
    ) throws Exception {
        var text = contractTextService.getText(documentId);
        if (text.pdf()) {
            response.setContentType("application/pdf");
        } else if (text.image()) {
            String lower = text.filename().toLowerCase();
            response.setContentType(lower.endsWith(".png") ? "image/png" : "image/jpeg");
        } else {
            response.setContentType("text/plain; charset=utf-8");
        }
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, HttpDownloadHeaders.inlineFilename(text.filename()));
        try (InputStream inputStream = ingestService.download(documentId)) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }
    }

    @GetMapping("/{documentId}/review")
    public ApiResponse<ContractReviewDto> getReview(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(contractReviewService.getReview(documentId));
    }

    @PostMapping("/{documentId}/review")
    public ApiResponse<ContractReviewDto> review(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(contractReviewService.review(documentId));
    }

    @PostMapping("/{documentId}/ingest-review")
    public ApiResponse<ContractReviewDto> ingestAndReview(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(contractReviewService.ingestAndReview(documentId));
    }

    @PostMapping("/{documentId}/parse-review")
    public ApiResponse<ContractReviewDto> parseReview(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(contractReviewService.parseOnly(documentId));
    }

    @PostMapping("/{documentId}/accept-revisions")
    public ApiResponse<ContractTextDto> acceptRevisions(@PathVariable("documentId") String documentId) {
        contractReviewService.acceptAllRisks(documentId);
        return ApiResponse.ok(contractTextService.getText(documentId));
    }

    @PostMapping("/{documentId}/risks/{riskId}/accept")
    public ApiResponse<Void> acceptRisk(
            @PathVariable("documentId") String documentId,
            @PathVariable("riskId") String riskId
    ) {
        contractReviewService.acceptRisk(documentId, riskId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{documentId}/risks/{riskId}/unaccept")
    public ApiResponse<Void> unacceptRisk(
            @PathVariable("documentId") String documentId,
            @PathVariable("riskId") String riskId
    ) {
        contractReviewService.unacceptRisk(documentId, riskId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{documentId}/export")
    public void export(
            @PathVariable("documentId") String documentId,
            @RequestParam(value = "format", defaultValue = "docx") String format,
            HttpServletResponse response
    ) throws Exception {
        ContractExportService.ExportFile file = contractExportService.export(documentId, format);
        response.setContentType(file.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, HttpDownloadHeaders.attachmentFilename(file.filename()));
        response.getOutputStream().write(file.bytes());
    }
}
