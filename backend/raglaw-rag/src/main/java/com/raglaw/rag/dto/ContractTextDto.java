package com.raglaw.rag.dto;

import java.util.List;

public record ContractTextDto(
        String documentId,
        String filename,
        String content,
        boolean pdf,
        String extractMethod,
        boolean ocrUsed,
        boolean image,
        List<ContractChunkDto> chunks
) {
}
