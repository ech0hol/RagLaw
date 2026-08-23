package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.contract.ContractExportService;
import com.raglaw.rag.contract.ContractReviewService;
import com.raglaw.rag.contract.ContractTextService;
import com.raglaw.rag.dto.ContractReviewDto;
import com.raglaw.rag.dto.ContractRiskDto;
import com.raglaw.rag.dto.ContractTextDto;
import com.raglaw.rag.service.IngestService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController {

    private final ContractReviewService contractReviewService;
    private final ContractTextService contractTextService;
    private final ContractExportService contractExportService;
    private final IngestService ingestService;

    public ContractController(
            ContractReviewService contractReviewService,
            ContractTextService contractTextService,
            ContractExportService contractExportService,
            IngestService ingestService
    ) {
        this.contractReviewService = contractReviewService;
        this.contractTextService = contractTextService;
        this.contractExportService = contractExportService;
        this.ingestService = ingestService;
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
        response.setContentType(text.pdf() ? "application/pdf" : "text/plain; charset=utf-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + text.filename() + "\"");
        try (InputStream inputStream = ingestService.download(documentId)) {
            StreamUtils.copy(inputStream, response.getOutputStream());
        }
    }

    @PostMapping("/{documentId}/review")
    public ApiResponse<ContractReviewDto> review(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(contractReviewService.review(documentId));
    }

    @PostMapping("/{documentId}/ingest-review")
    public ApiResponse<ContractReviewDto> ingestAndReview(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(contractReviewService.ingestAndReview(documentId));
    }

    @PostMapping("/{documentId}/accept-revisions")
    public ApiResponse<ContractTextDto> acceptRevisions(@PathVariable("documentId") String documentId) {
        contractReviewService.acceptAllRisks(documentId);
        String revised = contractTextService.buildRevisedText(documentId);
        ContractTextDto original = contractTextService.getText(documentId);
        return ApiResponse.ok(new ContractTextDto(
                original.documentId(),
                original.filename(),
                revised,
                original.pdf()
        ));
    }

    @PostMapping("/{documentId}/risks/{riskId}/accept")
    public ApiResponse<Void> acceptRisk(
            @PathVariable("documentId") String documentId,
            @PathVariable("riskId") String riskId
    ) {
        contractReviewService.acceptRisk(documentId, riskId);
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
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"");
        response.getOutputStream().write(file.bytes());
    }
}
