package com.raglaw.rag.seed;

import com.raglaw.common.util.Ids;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.repository.CategoryRepository;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
@Order(1)
public class CategoryL3Bootstrap implements ApplicationRunner {

    private static final Set<String> KNOWLEDGE_DOC_TYPES = Set.of("STATUTE", "CASE");

    private final CategoryRepository categoryRepository;

    public CategoryL3Bootstrap(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrapMissingL3Leaves();
    }

    int bootstrapMissingL3Leaves() {
        List<CategoryEntity> enabled = categoryRepository.findByEnabledTrueOrderBySortOrderAsc();
        int created = 0;
        for (CategoryEntity l2 : enabled) {
            if (l2.getLevel() != 2 || !KNOWLEDGE_DOC_TYPES.contains(l2.getDocType())) {
                continue;
            }
            if (hasEnabledL3Child(l2.getId())) {
                continue;
            }
            String code = l2.getCode() + "_GENERAL";
            if (categoryRepository.existsByCode(code)) {
                continue;
            }
            categoryRepository.save(new CategoryEntity(
                    Ids.newId(),
                    l2.getId(),
                    3,
                    code,
                    l2.getName(),
                    l2.getPath() + "/GENERAL",
                    l2.getDocType(),
                    0
            ));
            created++;
        }
        return created;
    }

    private boolean hasEnabledL3Child(String parentId) {
        return categoryRepository.findByParentIdOrderBySortOrderAsc(parentId).stream()
                .anyMatch(child -> child.getLevel() == 3 && child.isEnabled());
    }
}
