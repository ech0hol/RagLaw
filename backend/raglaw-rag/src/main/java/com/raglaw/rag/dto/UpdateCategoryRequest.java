package com.raglaw.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCategoryRequest(
        @NotBlank String name,
        Integer sortOrder,
        Boolean enabled
) {
}
