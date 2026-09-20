package com.raglaw.memory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaskNoteServiceTest {
    private final TaskNoteService service = new TaskNoteService();

    @Test
    void mergePreservesQuestionsAndDeduplicatesSources() {
        TaskNote current = new TaskNote("审查", List.of("m1"), List.of("d1"),
                List.of("缺少签署日期", "确认付款方"), List.of("s1"), 2);
        TaskNote delta = new TaskNote("", List.of("m2"), List.of("d2"),
                List.of("确认付款方", "确认终止条款"), List.of("s1", "s2"), 3);

        TaskNote merged = service.merge(current, delta, Set.of("m1", "m2"), Set.of("缺少签署日期", "确认付款方"));

        assertThat(merged.objective()).isEqualTo("审查");
        assertThat(merged.confirmedFactIds()).containsExactly("m1", "m2");
        assertThat(merged.unresolvedQuestions()).containsExactly("确认终止条款");
        assertThat(merged.sourceIds()).containsExactly("s1", "s2");
        assertThat(merged.snapshotVersion()).isEqualTo(3);
    }

    @Test
    void rejectsFactNotVisibleAtNoteSnapshot() {
        TaskNote current = new TaskNote("咨询", List.of("m1"), List.of(), List.of(), List.of(), 4);
        TaskNote delta = new TaskNote("", List.of("m2"), List.of(), List.of(), List.of(), 5);

        assertThatThrownBy(() -> service.merge(current, delta, Set.of("m1"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newer");
    }
}
