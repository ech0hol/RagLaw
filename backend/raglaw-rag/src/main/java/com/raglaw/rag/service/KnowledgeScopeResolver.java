package com.raglaw.rag.service;

import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeScopeResolver {

    private final CategoryRepository categoryRepository;

    public KnowledgeScopeResolver(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 将 Agent 配置中的 knowledge scope（类目 code 或 path）展开为 chunk 检索用的 l2/l3 path 列表。
     */
    public List<String> resolvePaths(List<String> knowledgeScopes) {
        if (knowledgeScopes == null || knowledgeScopes.isEmpty()) {
            return List.of();
        }
        Set<String> paths = new LinkedHashSet<>();
        for (String scope : knowledgeScopes) {
            if (scope == null || scope.isBlank()) {
                continue;
            }
            if (scope.startsWith("/")) {
                paths.add(scope);
                continue;
            }
            categoryRepository.findByCode(scope).ifPresent(category -> addCategoryPaths(category, paths));
        }
        return new ArrayList<>(paths);
    }

    private void addCategoryPaths(CategoryEntity category, Set<String> paths) {
        paths.add(category.getPath());
        if (category.getLevel() <= 2) {
            categoryRepository.findByParentIdOrderBySortOrderAsc(category.getId()).forEach(child -> {
                paths.add(child.getPath());
                if (child.getLevel() == 2) {
                    categoryRepository.findByParentIdOrderBySortOrderAsc(child.getId())
                            .forEach(grand -> paths.add(grand.getPath()));
                }
            });
        }
    }
}
