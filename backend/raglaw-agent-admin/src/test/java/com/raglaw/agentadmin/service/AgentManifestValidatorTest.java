package com.raglaw.agentadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolGrant;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentManifestValidatorTest {
    @Test
    void rejectsUnknownToolAndMcpWideGrant() {
        AgentVersionSnapshot snapshot = snapshot(new AgentToolPolicy(List.of(
                new AgentToolGrant("unknown", "READ_ONLY", false, Set.of(), 1_000)), Set.of("tavily")));
        AgentManifestValidation result = new AgentManifestValidator().validate(snapshot);
        assertThat(result.errors()).contains("UNKNOWN_TOOL:unknown");
    }

    @Test
    void rejectsNetworkToolWithoutIndividualGrant() {
        AgentVersionSnapshot snapshot = snapshot(new AgentToolPolicy(List.of(), Set.of("tavily")));
        AgentManifestValidation result = new AgentManifestValidator().validate(snapshot);
        assertThat(result.errors()).doesNotContain("MCP_TOOL_WITHOUT_SERVER:tavily-search");
        assertThat(result.valid()).isTrue();
    }

    static AgentVersionSnapshot snapshot(AgentToolPolicy policy) {
        return new AgentVersionSnapshot("LABOR", 1, AgentPublishStatus.DRAFT, "dashscope:qwen-plus", "prompt",
                new AgentCapabilityManifest(Set.of("LABOR_LAW"), Set.of("LEGAL_ANALYSIS"), Set.of("LEGAL_ANALYSIS"), Set.of("HIGH"), Set.of(), "Result"),
                policy, List.of(), List.of(), List.of(), 0.9, "sha");
    }
}
