package com.raglaw.rag.service;

import com.raglaw.rag.dto.KnowledgeHitDto;
import com.raglaw.rag.dto.KnowledgeSearchPageDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService {

    private final DocumentKnowledgeSearchService documentKnowledgeSearchService;

    public KnowledgeSearchService(DocumentKnowledgeSearchService documentKnowledgeSearchService) {
        this.documentKnowledgeSearchService = documentKnowledgeSearchService;
    }

    public List<KnowledgeHitDto> search(String query, String docType, int limit) {
        return searchPage(query, docType, null, 0, limit).items();
    }

    public KnowledgeSearchPageDto searchPage(
            String query,
            String docType,
            String l2Path,
            int page,
            int pageSize
    ) {
        if (query == null || query.isBlank()) {
            return new KnowledgeSearchPageDto(List.of(), page, pageSize, 0);
        }
        int fetchLimit = Math.max(pageSize, 1) * (page + 1) * 2;
        List<String> knowledgeScopes = scopesForDocType(docType);
        List<KnowledgeHitDto> all = documentKnowledgeSearchService.search(
                query,
                knowledgeScopes,
                docType,
                l2Path,
                fetchLimit
        );

        int from = Math.max(page, 0) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<KnowledgeHitDto> pageItems = from >= all.size() ? List.of() : all.subList(from, to);
        return new KnowledgeSearchPageDto(pageItems, page, pageSize, all.size());
    }

    private static List<String> scopesForDocType(String docType) {
        if (docType == null || docType.isBlank()) {
            return List.of("STATUTE", "CASE");
        }
        return List.of(docType);
    }
}
