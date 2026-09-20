package com.raglaw.agentadmin.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentVersionRegistryTest {
    @Test
    void frozenDeprecatedVersionRemainsReadableButNotRoutable() {
        AgentVersionSnapshot v1 = snapshot(1, AgentPublishStatus.DEPRECATED);
        AgentVersionSnapshot v2 = snapshot(2, AgentPublishStatus.PUBLISHED);
        AgentVersionRegistry registry = new AgentVersionRegistry();
        registry.reload(List.of(v1, v2));

        assertThat(registry.publishedCandidates()).extracting(AgentVersionSnapshot::version).containsExactly(2);
        assertThat(registry.get("LABOR", 1).status()).isEqualTo(AgentPublishStatus.DEPRECATED);
    }

    private static AgentVersionSnapshot snapshot(int version, AgentPublishStatus status) {
        return new AgentVersionSnapshot("LABOR", version, status, "dashscope:qwen-plus", "prompt",
                new AgentCapabilityManifest(Set.of("LABOR"), Set.of("LEGAL_ANALYSIS"), Set.of("LEGAL_ANALYSIS"), Set.of("LOW"), Set.of(), "Answer"),
                new AgentToolPolicy(List.of(), Set.of()), List.of(), List.of(), List.of(), 0.9, "sha256:" + version);
    }
}
