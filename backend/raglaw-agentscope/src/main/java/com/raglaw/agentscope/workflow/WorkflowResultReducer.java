package com.raglaw.agentscope.workflow;

import com.raglaw.memory.service.CaseMemorySnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WorkflowResultReducer {
    public WorkflowReduction reduce(List<NodeExecutionResult> results, CaseMemorySnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot");
        List<NodeExecutionResult> accepted = results == null ? List.of() : results.stream()
                .filter(result -> result != null && result.snapshotVersion() == snapshot.version())
                .toList();
        List<WorkflowConflict> conflicts = new ArrayList<>();
        if (results != null) results.stream().filter(result -> result != null && result.snapshotVersion() > snapshot.version())
                .forEach(result -> conflicts.add(new WorkflowConflict("STALE_SNAPSHOT", List.of(result.idempotencyKey()), List.of(), true)));

        Map<String, List<Claim>> bySlot = new LinkedHashMap<>();
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (NodeExecutionResult result : accepted) {
            Object facts = result.structuredOutput().get("facts");
            if (!(facts instanceof List<?> list)) continue;
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> raw)) continue;
                String slot = String.valueOf(raw.get("slot") == null ? "" : raw.get("slot")).trim();
                String factValue = String.valueOf(raw.get("value") == null ? "" : raw.get("value")).trim();
                if (slot.isBlank() || factValue.isBlank()) continue;
                Map<String, Object> normalized = new LinkedHashMap<>();
                raw.forEach((key, item) -> normalized.put(String.valueOf(key), item));
                Claim claim = new Claim(result.idempotencyKey(), factValue, normalized);
                bySlot.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(claim);
            }
        }
        for (var entry : bySlot.entrySet()) {
            LinkedHashSet<String> distinct = entry.getValue().stream().map(Claim::value).collect(Collectors.toCollection(LinkedHashSet::new));
            if (distinct.size() > 1) {
                conflicts.add(new WorkflowConflict(entry.getKey(), entry.getValue().stream().map(Claim::resultId).toList(), List.copyOf(distinct), true));
            } else {
                candidates.add(entry.getValue().get(0).raw());
            }
        }
        return new WorkflowReduction(conflicts.isEmpty() ? candidates : List.of(), conflicts, accepted);
    }

    private record Claim(String resultId, String value, Map<String, Object> raw) {}
}
