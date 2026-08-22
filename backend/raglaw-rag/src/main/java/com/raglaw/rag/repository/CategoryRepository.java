package com.raglaw.rag.repository;

import com.raglaw.rag.domain.CategoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, String> {

    List<CategoryEntity> findByParentIdOrderBySortOrderAsc(String parentId);

    List<CategoryEntity> findByParentIdIsNullOrderBySortOrderAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    List<CategoryEntity> findByEnabledTrueOrderBySortOrderAsc();
}
