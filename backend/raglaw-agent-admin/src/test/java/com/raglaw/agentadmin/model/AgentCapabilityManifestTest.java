package com.raglaw.agentadmin.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.agentadmin.domain.AgentPublishStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentCapabilityManifestTest {
    @Test
    void rejectsBlankCapabilityAndEmptyRiskLevels() {
        assertThatThrownBy(() -> new AgentCapabilityManifest(
                Set.of("LABOR_LAW"), Set.of(" "), Set.of("LEGAL_ANALYSIS"),
                Set.of(), Set.of("CASE_FACTS"), "LegalAnalysisResult"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishedSnapshotCopiesMutableCollections() {
        var capabilities = new HashSet<>(Set.of("LEGAL_ANALYSIS"));
        var snapshot = new AgentVersionSnapshot("LABOR", 1, AgentPublishStatus.PUBLISHED,
                "dashscope:qwen-plus", "prompt", new AgentCapabilityManifest(
                Set.of("LABOR_LAW"), capabilities, Set.of("LEGAL_ANALYSIS"), Set.of("LOW"), Set.of(), "Result"),
                new AgentToolPolicy(List.of(), Set.of()), List.of(), List.of(), List.of(), 0.9, "sha");
        capabilities.add("UNSAFE_CHANGE");
        assertThat(snapshot.manifest().capabilities()).containsExactly("LEGAL_ANALYSIS");
    }
}
