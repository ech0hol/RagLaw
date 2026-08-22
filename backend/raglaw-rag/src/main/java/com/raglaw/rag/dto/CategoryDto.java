package com.raglaw.rag.dto;

import com.raglaw.rag.domain.CategoryEntity;
import java.util.ArrayList;
import java.util.List;

public record CategoryDto(
        String id,
        String parentId,
        int level,
        String code,
        String name,
        String path,
        String docType,
        int sortOrder,
        boolean enabled
) {

    public static CategoryDto from(CategoryEntity entity) {
        return new CategoryDto(
                entity.getId(),
                entity.getParentId(),
                entity.getLevel(),
                entity.getCode(),
                entity.getName(),
                entity.getPath(),
                entity.getDocType(),
                entity.getSortOrder(),
                entity.isEnabled()
        );
    }
}
