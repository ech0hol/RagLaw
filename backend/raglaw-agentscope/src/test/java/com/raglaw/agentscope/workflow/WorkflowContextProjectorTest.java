package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.service.CaseMemorySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowContextProjectorTest {
    @Test
    void nodeSeesOnlyDependenciesAndAllowedCaseFields() {
        WorkflowDefinition workflow = new WorkflowDefinition("w", Set.of(), Set.of(), List.of(
                new WorkflowNodeDefinition("fact-extraction", "FACT", List.of(), false),
                new WorkflowNodeDefinition("legal-analysis", "LEGAL", List.of("fact-extraction"), false)));
        var snapshot = new CaseMemorySnapshot(new CaseScope("tenant", "user", "case"), 18, Instant.now());
        var resolved = new ResolvedAgent("FACT", "fact", 1, Set.of("rag_search"), "sha", 0.8, List.of());
        var legal = new ResolvedAgent("LEGAL", "legal", 1, Set.of(), "sha", 0.8, List.of());
        var manifest = new WorkflowManifestFactory(new ObjectMapper()).create(new WorkflowManifestFactory.ManifestRequest(
                "run", "tenant", "user", "case", "conv", workflow, 1, "route", snapshot, "risk", "tools", Map.of("FACT", resolved, "LEGAL", legal), Set.of()));
        var factResult = new NodeExecutionResult("fact-extraction", "key", "SUCCEEDED", Map.of("x", "y"), List.of("e1"), 18);
        var state = new SharedWorkflowState(manifest, snapshot, new WorkflowTaskNote(1, Map.of()),
                List.of(new WorkflowCaseFact("m1", "salary", "15000", "ACTIVE", "SHARED", List.of("s1")),
                        new WorkflowCaseFact("m2", "private", "secret", "ACTIVE", "PRIVATE_OTHER_ROLE", List.of("s2"))),
                List.of(), Map.of("fact-extraction", factResult));

        WorkflowContextView view = new WorkflowContextProjector().project("legal-analysis", state);
        assertThat(view.dependencyResults()).containsOnlyKeys("fact-extraction");
        assertThat(view.caseFacts()).extracting(WorkflowCaseFact::memoryId).containsExactly("m1");
        assertThat(view.memorySnapshotVersion()).isEqualTo(18);
    }
}
