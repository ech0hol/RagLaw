package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.dto.ApprovalActionRequest;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.service.ApprovalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/pending")
    public ApiResponse<List<DocumentDto>> pending() {
        return ApiResponse.ok(approvalService.listPending());
    }

    @PostMapping("/{documentId}/approve")
    public ApiResponse<DocumentDto> approve(@PathVariable String documentId) {
        return ApiResponse.ok(approvalService.approve(documentId));
    }

    @PostMapping("/{documentId}/reject")
    public ApiResponse<DocumentDto> reject(
            @PathVariable String documentId,
            @Valid @RequestBody ApprovalActionRequest request
    ) {
        return ApiResponse.ok(approvalService.reject(documentId, request.reason()));
    }
}
