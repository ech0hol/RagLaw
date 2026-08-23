package com.raglaw.rag.dto;

import java.util.List;

public record KnowledgeSearchPageDto(
        List<KnowledgeHitDto> items,
        int page,
        int pageSize,
        int total
) {
}
