package com.raglaw.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.dto.CategoryDto;
import com.raglaw.rag.dto.CategoryTreeNode;
import com.raglaw.rag.dto.UpdateCategoryRequest;
import com.raglaw.rag.repository.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    void enabledTreeHasThreeRootsAndFourL3NodesWithChineseNames() {
        when(categoryRepository.findByEnabledTrueOrderBySortOrderAsc()).thenReturn(mvpCategories());

        List<CategoryTreeNode> tree = categoryService.getTree(true);

        assertEquals(3, tree.size());
        assertEquals(4, countLevel(tree, 3));
        CategoryTreeNode caseLabor = findById(tree, "cat_l3_case_civil_labor");
        assertEquals("劳动争议案例", caseLabor.name());
    }

    @Test
    void updateTogglesEnabledFlag() {
        CategoryEntity entity = category(
                "cat_l2_statute_civil", "cat_l1_statute", 2, "STATUTE_CIVIL", "民法商法", "/STATUTE/CIVIL", "STATUTE", 2
        );
        when(categoryRepository.findById("cat_l2_statute_civil")).thenReturn(Optional.of(entity));
        when(categoryRepository.save(entity)).thenReturn(entity);

        CategoryDto updated = categoryService.update(
                "cat_l2_statute_civil",
                new UpdateCategoryRequest("民法商法（更新）", null, false)
        );

        assertEquals("民法商法（更新）", updated.name());
        assertEquals(false, updated.enabled());
        assertEquals(false, entity.isEnabled());
    }

    private static List<CategoryEntity> mvpCategories() {
        return List.of(
                category("cat_l1_statute", null, 1, "STATUTE", "法规", "/STATUTE", "STATUTE", 1),
                category("cat_l1_case", null, 1, "CASE", "案例", "/CASE", "CASE", 2),
                category("cat_l1_contract", null, 1, "CONTRACT", "合同", "/CONTRACT", "CONTRACT", 3),
                category("cat_l2_statute_civil", "cat_l1_statute", 2, "STATUTE_CIVIL", "民法商法", "/STATUTE/CIVIL", "STATUTE", 2),
                category("cat_l2_case_civil", "cat_l1_case", 2, "CASE_CIVIL", "民事案例", "/CASE/CIVIL", "CASE", 1),
                category("cat_l2_contract_civil", "cat_l1_contract", 2, "CONTRACT_CIVIL", "民事合同实务", "/CONTRACT/CIVIL", "CONTRACT", 1),
                category("cat_l3_statute_civil_labor", "cat_l2_statute_civil", 3, "STATUTE_CIVIL_LABOR", "劳动合同法规", "/STATUTE/CIVIL/LABOR", "STATUTE", 1),
                category("cat_l3_statute_civil_contract", "cat_l2_statute_civil", 3, "STATUTE_CIVIL_CONTRACT", "合同法规", "/STATUTE/CIVIL/CONTRACT", "STATUTE", 2),
                category("cat_l3_case_civil_labor", "cat_l2_case_civil", 3, "CASE_CIVIL_LABOR", "劳动争议案例", "/CASE/CIVIL/LABOR", "CASE", 1),
                category("cat_l3_contract_civil_general", "cat_l2_contract_civil", 3, "CONTRACT_CIVIL_GENERAL", "民事合同审查", "/CONTRACT/CIVIL/GENERAL", "CONTRACT", 1)
        );
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

    private static int countLevel(List<CategoryTreeNode> nodes, int level) {
        int count = 0;
        for (CategoryTreeNode node : nodes) {
            if (node.level() == level) {
                count++;
            }
            count += countLevel(node.children(), level);
        }
        return count;
    }

    private static CategoryTreeNode findById(List<CategoryTreeNode> nodes, String id) {
        for (CategoryTreeNode node : nodes) {
            if (id.equals(node.id())) {
                return node;
            }
            CategoryTreeNode found = findById(node.children(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void adminTreeIncludesDisabledNodes() {
        CategoryEntity caseL1 = category("cat_l1_case", null, 1, "CASE", "案例", "/CASE", "CASE", 2);
        CategoryEntity disabled = category(
                "cat_l2_case_criminal", "cat_l1_case", 2, "CASE_CRIMINAL", "刑事案例", "/CASE/CRIMINAL", "CASE", 2
        );
        disabled.setEnabled(false);
        when(categoryRepository.findAll()).thenReturn(List.of(caseL1, disabled));

        List<CategoryTreeNode> tree = categoryService.getTree(false);

        assertEquals(1, tree.size());
        assertTrue(tree.get(0).children().stream().anyMatch(node -> !node.enabled()));
    }
}
