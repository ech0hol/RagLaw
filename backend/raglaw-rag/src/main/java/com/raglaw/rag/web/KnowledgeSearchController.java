package com.raglaw.rag.web;

import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.dto.KnowledgeHitDto;
import com.raglaw.rag.service.KnowledgeSearchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeSearchController {

    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeSearchController(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @GetMapping("/search")
    public ApiResponse<List<KnowledgeHitDto>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return ApiResponse.ok(knowledgeSearchService.search(query, docType, Math.min(limit, 20)));
    }
}
