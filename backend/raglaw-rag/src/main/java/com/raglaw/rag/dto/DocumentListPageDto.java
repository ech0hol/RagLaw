package com.raglaw.rag.dto;

import java.util.List;

public record DocumentListPageDto(
        List<DocumentDto> items,
        int page,
        int pageSize,
        int total
) {
}
