package com.raglaw.memory.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ContextBudgetPolicyTest {
    @Test
    void reservesOutputAndNeverDropsRequiredSections() {
        NodeContextProfile profile = new ContextProfileCatalog().require("LEGAL_ANALYSIS");
        ContextBudget budget = new ContextBudgetPolicy().budgetFor(new ModelWindow(32_000), profile);
        assertThat(budget.outputReserve()).isGreaterThanOrEqualTo(6_400);
        assertThat(budget.inputLimit()).isLessThanOrEqualTo(25_600);
        assertThat(profile.priority(ContextSectionType.CURRENT_TASK)).isEqualTo(ContextPriority.P0_REQUIRED);
        assertThat(profile.priority(ContextSectionType.DISPUTED_FACTS)).isEqualTo(ContextPriority.P1_HIGH);
    }

    @Test
    void rejectsUnsafeReserveConfiguration() {
        assertThatThrownBy(() -> new ContextBudgetPolicy(0.19)).isInstanceOf(IllegalArgumentException.class);
    }
}
