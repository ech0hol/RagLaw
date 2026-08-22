package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.dto.CategoryDto;
import com.raglaw.rag.dto.CategoryTreeBuilder;
import com.raglaw.rag.dto.CategoryTreeNode;
import com.raglaw.rag.dto.CreateCategoryRequest;
import com.raglaw.rag.dto.UpdateCategoryRequest;
import com.raglaw.rag.repository.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeNode> getTree(boolean enabledOnly) {
        List<CategoryEntity> categories = enabledOnly
                ? categoryRepository.findByEnabledTrueOrderBySortOrderAsc()
                : categoryRepository.findAll();
        return CategoryTreeBuilder.buildTree(categories);
    }

    @Transactional(readOnly = true)
    public CategoryDto getById(String id) {
        return CategoryDto.from(findEntity(id));
    }

    @Transactional
    public CategoryDto create(CreateCategoryRequest request) {
        if (categoryRepository.existsByCode(request.code())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "类目编码已存在: " + request.code());
        }
        CategoryEntity parent = resolveParent(request.parentId(), request.level());
        String path = buildPath(parent, request.code());
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
        boolean enabled = request.enabled() == null || request.enabled();

        CategoryEntity entity = new CategoryEntity(
                Ids.newId(),
                request.parentId(),
                request.level(),
                request.code(),
                request.name(),
                path,
                request.docType(),
                sortOrder
        );
        entity.setEnabled(enabled);
        return CategoryDto.from(categoryRepository.save(entity));
    }

    @Transactional
    public CategoryDto update(String id, UpdateCategoryRequest request) {
        CategoryEntity entity = findEntity(id);
        entity.setName(request.name());
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        return CategoryDto.from(categoryRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CategoryEntity entity = findEntity(id);
        List<CategoryEntity> children = categoryRepository.findByParentIdOrderBySortOrderAsc(id);
        if (!children.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "存在子类目，无法删除");
        }
        categoryRepository.delete(entity);
    }

    CategoryEntity findEntity(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "类目不存在"));
    }

    private CategoryEntity resolveParent(String parentId, int level) {
        if (level == 1) {
            if (parentId != null && !parentId.isBlank()) {
                throw new BusinessException(ErrorCodes.VALIDATION, "L1 类目不能有父节点");
            }
            return null;
        }
        if (parentId == null || parentId.isBlank()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "L2/L3 类目必须指定父节点");
        }
        CategoryEntity parent = findEntity(parentId);
        if (parent.getLevel() != level - 1) {
            throw new BusinessException(ErrorCodes.VALIDATION, "父类目层级不匹配");
        }
        return parent;
    }

    private static String buildPath(CategoryEntity parent, String code) {
        if (parent == null) {
            return "/" + code;
        }
        return parent.getPath() + "/" + code;
    }
}
