package com.raglaw.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.repository.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeScopeResolverTest {

    @Mock
    private CategoryRepository categoryRepository;

    private KnowledgeScopeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new KnowledgeScopeResolver(categoryRepository);
    }

    @Test
    void resolvesCategoryCodeToL2AndL3Paths() {
        CategoryEntity l2 = new CategoryEntity(
                "cat_l2_statute_civil", "cat_l1_statute", 2,
                "STATUTE_CIVIL", "民法商法", "/STATUTE/CIVIL", "STATUTE", 2
        );
        CategoryEntity l3Labor = new CategoryEntity(
                "cat_l3_statute_civil_labor", "cat_l2_statute_civil", 3,
                "STATUTE_CIVIL_LABOR", "劳动合同法专题", "/STATUTE/CIVIL/LABOR", "STATUTE", 1
        );
        CategoryEntity l3Contract = new CategoryEntity(
                "cat_l3_statute_civil_contract", "cat_l2_statute_civil", 3,
                "STATUTE_CIVIL_CONTRACT", "合同法专题", "/STATUTE/CIVIL/CONTRACT", "STATUTE", 2
        );

        when(categoryRepository.findByCode("cat_l2_statute_civil")).thenReturn(Optional.of(l2));
        when(categoryRepository.findByParentIdOrderBySortOrderAsc("cat_l2_statute_civil"))
                .thenReturn(List.of(l3Labor, l3Contract));

        List<String> paths = resolver.resolvePaths(List.of("cat_l2_statute_civil"));

        assertThat(paths).containsExactly(
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR",
                "/STATUTE/CIVIL/CONTRACT"
        );
    }

    @Test
    void acceptsDirectPathScopes() {
        List<String> paths = resolver.resolvePaths(List.of("/STATUTE/CIVIL/LABOR"));

        assertThat(paths).containsExactly("/STATUTE/CIVIL/LABOR");
    }
}
