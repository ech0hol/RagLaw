package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.service.CaseMemorySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowManifestFactoryTest {
    @Test
    void freezesEveryNodeAndSnapshotBeforeExecution() {
        WorkflowDefinition workflow = new WorkflowDefinition("labor", Set.of(), Set.of(), List.of(new WorkflowNodeDefinition("legal", "LEGAL", List.of(), false)));
        ResolvedAgent resolved = new ResolvedAgent("LEGAL", "labor_expert", 3, Set.of("rag_search"), "sha", 0.9, List.of());
        var request = new WorkflowManifestFactory.ManifestRequest("run", "tenant", "user", "case", "conversation", workflow, 1, "route", new CaseMemorySnapshot(new CaseScope("tenant", "user", "case"), 18, Instant.now()), "risk-v1", "tools-v1", Map.of("LEGAL", resolved), Set.of());
        WorkflowExecutionManifest manifest = new WorkflowManifestFactory(new ObjectMapper()).create(request);
        assertThat(manifest.memorySnapshotVersion()).isEqualTo(18);
        assertThat(manifest.nodes().get("legal").agentVersion()).isEqualTo(3);
        assertThatThrownBy(() -> manifest.nodes().put("new-node", manifest.nodes().get("legal"))).isInstanceOf(UnsupportedOperationException.class);
    }
}
