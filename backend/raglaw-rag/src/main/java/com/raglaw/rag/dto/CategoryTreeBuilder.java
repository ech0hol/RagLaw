package com.raglaw.rag.dto;

import com.raglaw.rag.domain.CategoryEntity;
import java.util.ArrayList;
import java.util.List;

public final class CategoryTreeBuilder {

    private CategoryTreeBuilder() {
    }

    public static List<CategoryTreeNode> buildTree(List<CategoryEntity> categories) {
        List<CategoryEntity> roots = categories.stream()
                .filter(c -> c.getParentId() == null)
                .toList();
        return roots.stream()
                .map(root -> buildNode(root, categories))
                .toList();
    }

    private static CategoryTreeNode buildNode(CategoryEntity node, List<CategoryEntity> all) {
        List<CategoryTreeNode> children = all.stream()
                .filter(c -> node.getId().equals(c.getParentId()))
                .map(child -> buildNode(child, all))
                .toList();
        return CategoryTreeNode.from(node, new ArrayList<>(children));
    }
}
