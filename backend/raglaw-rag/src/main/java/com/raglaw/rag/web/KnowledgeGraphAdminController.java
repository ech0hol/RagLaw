package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.dto.CreateCaseStatuteRefRequest;
import com.raglaw.rag.dto.RelatedDocumentDto;
import com.raglaw.rag.service.KnowledgeGraphService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class KnowledgeGraphAdminController {

    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeGraphAdminController(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @PostMapping("/refs")
    public ApiResponse<RelatedDocumentDto> createRef(@Valid @RequestBody CreateCaseStatuteRefRequest request) {
        return ApiResponse.ok(knowledgeGraphService.createRef(request));
    }
}
