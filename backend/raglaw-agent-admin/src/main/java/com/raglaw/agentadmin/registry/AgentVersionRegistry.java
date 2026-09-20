package com.raglaw.agentadmin.registry;

import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AgentVersionRegistry {
    private volatile Map<String, List<AgentVersionSnapshot>> published = Map.of();
    private volatile Map<String, List<AgentVersionSnapshot>> allVersions = Map.of();

    public void reload(Collection<AgentVersionSnapshot> snapshots) {
        Map<String, List<AgentVersionSnapshot>> all = snapshots.stream()
                .collect(Collectors.groupingBy(AgentVersionSnapshot::agentCode,
                        Collectors.collectingAndThen(Collectors.toList(), values -> values.stream()
                                .sorted(Comparator.comparingInt(AgentVersionSnapshot::version).reversed()).toList())));
        Map<String, List<AgentVersionSnapshot>> next = all.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream().filter(snapshot -> snapshot.status() == AgentPublishStatus.PUBLISHED).toList()));
        this.allVersions = Collections.unmodifiableMap(new ConcurrentHashMap<>(all));
        this.published = Collections.unmodifiableMap(new ConcurrentHashMap<>(next));
    }

    public List<AgentVersionSnapshot> publishedCandidates() {
        return published.values().stream().flatMap(List::stream).toList();
    }

    public AgentVersionSnapshot latestPublished(String agentCode) {
        return published.getOrDefault(agentCode, List.of()).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No published expert: " + agentCode));
    }

    public AgentVersionSnapshot get(String agentCode, int version) {
        return allVersions.getOrDefault(agentCode, List.of()).stream()
                .filter(snapshot -> snapshot.version() == version).findFirst().orElse(null);
    }
}
