package com.raglaw.rag.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.repository.CategoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryL3BootstrapTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryL3Bootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new CategoryL3Bootstrap(categoryRepository);
    }

    @Test
    void createsGeneralL3WhenL2HasNoEnabledChildren() {
        CategoryEntity adminL2 = category(
                "cat_l2_statute_admin", "cat_l1_statute", 2, "STATUTE_ADMIN", "行政法", "/STATUTE/ADMIN", "STATUTE", 3
        );
        when(categoryRepository.findByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of(adminL2));
        when(categoryRepository.findByParentIdOrderBySortOrderAsc("cat_l2_statute_admin")).thenReturn(List.of());
        when(categoryRepository.existsByCode("STATUTE_ADMIN_GENERAL")).thenReturn(false);

        int created = bootstrap.bootstrapMissingL3Leaves();

        assertEquals(1, created);
        ArgumentCaptor<CategoryEntity> captor = ArgumentCaptor.forClass(CategoryEntity.class);
        verify(categoryRepository).save(captor.capture());
        CategoryEntity saved = captor.getValue();
        assertEquals(3, saved.getLevel());
        assertEquals("STATUTE_ADMIN_GENERAL", saved.getCode());
        assertEquals("行政法", saved.getName());
        assertEquals("/STATUTE/ADMIN/GENERAL", saved.getPath());
        assertEquals("cat_l2_statute_admin", saved.getParentId());
    }

    @Test
    void skipsL2ThatAlreadyHasEnabledL3() {
        CategoryEntity civilL2 = category(
                "cat_l2_statute_civil", "cat_l1_statute", 2, "STATUTE_CIVIL", "民法商法", "/STATUTE/CIVIL", "STATUTE", 2
        );
        CategoryEntity laborL3 = category(
                "cat_l3_statute_civil_labor", "cat_l2_statute_civil", 3, "STATUTE_CIVIL_LABOR", "劳动合同法规",
                "/STATUTE/CIVIL/LABOR", "STATUTE", 1
        );
        when(categoryRepository.findByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of(civilL2));
        when(categoryRepository.findByParentIdOrderBySortOrderAsc("cat_l2_statute_civil")).thenReturn(List.of(laborL3));

        int created = bootstrap.bootstrapMissingL3Leaves();

        assertEquals(0, created);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void skipsWhenGeneralCodeAlreadyExists() {
        CategoryEntity adminL2 = category(
                "cat_l2_statute_admin", "cat_l1_statute", 2, "STATUTE_ADMIN", "行政法", "/STATUTE/ADMIN", "STATUTE", 3
        );
        when(categoryRepository.findByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of(adminL2));
        when(categoryRepository.findByParentIdOrderBySortOrderAsc("cat_l2_statute_admin")).thenReturn(List.of());
        when(categoryRepository.existsByCode("STATUTE_ADMIN_GENERAL")).thenReturn(true);

        int created = bootstrap.bootstrapMissingL3Leaves();

        assertEquals(0, created);
        verify(categoryRepository, never()).save(any());
    }

    private static CategoryEntity category(
            String id,
            String parentId,
            int level,
            String code,
            String name,
            String path,
            String docType,
            int sortOrder
    ) {
        CategoryEntity entity = new CategoryEntity(id, parentId, level, code, name, path, docType, sortOrder);
        entity.setEnabled(true);
        return entity;
    }
}
