package com.raglaw.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCategoryRequest(
        String parentId,
        @NotNull @Min(1) @Max(3) Integer level,
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String docType,
        Integer sortOrder,
        Boolean enabled
) {
}
