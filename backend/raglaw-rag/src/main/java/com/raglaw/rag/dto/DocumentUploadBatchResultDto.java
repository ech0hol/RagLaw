package com.raglaw.rag.dto;

import java.util.List;

public record DocumentUploadBatchResultDto(
        List<DocumentDto> items,
        String ingestMode
) {
}
