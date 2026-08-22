package com.raglaw.rag.dto;

import com.raglaw.rag.domain.CategoryEntity;
import java.util.List;

public record CategoryTreeNode(
        String id,
        String parentId,
        int level,
        String code,
        String name,
        String path,
        String docType,
        int sortOrder,
        boolean enabled,
        List<CategoryTreeNode> children
) {

    public static CategoryTreeNode from(CategoryEntity entity, List<CategoryTreeNode> children) {
        return new CategoryTreeNode(
                entity.getId(),
                entity.getParentId(),
                entity.getLevel(),
                entity.getCode(),
                entity.getName(),
                entity.getPath(),
                entity.getDocType(),
                entity.getSortOrder(),
                entity.isEnabled(),
                children
        );
    }
}
