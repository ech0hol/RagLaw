package com.raglaw.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.memory.context.ContextBudgetPolicy;
import com.raglaw.memory.service.CaseMemoryQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AssignableTypeFilter;

class ContextAssemblerBeanNamingTest {

    @Test
    void registersBothContextAssemblersWithDistinctBeanNames() {
        try (AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()) {
            applicationContext.registerBean(CaseMemoryQueryService.class, () -> request -> List.of());
            applicationContext.registerBean(ContextBudgetPolicy.class);

            ClassPathBeanDefinitionScanner scanner =
                    new ClassPathBeanDefinitionScanner(applicationContext, false);
            scanner.addIncludeFilter(new AssignableTypeFilter(
                    com.raglaw.memory.service.ContextAssembler.class));
            scanner.addIncludeFilter(new AssignableTypeFilter(
                    com.raglaw.memory.context.ContextAssembler.class));
            scanner.scan("com.raglaw.memory.service", "com.raglaw.memory.context");

            applicationContext.refresh();

            assertThat(applicationContext.containsBean("caseMemoryContextAssembler")).isTrue();
            assertThat(applicationContext.containsBean("governedContextAssembler")).isTrue();
        }
    }
}
