package com.raglaw.agentadmin.registry;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AgentRegistry {

    private volatile Map<String, AgentConfigSnapshot> agents = Map.of();

    public void reload(Collection<AgentConfigSnapshot> snapshots) {
        Map<String, AgentConfigSnapshot> next = new ConcurrentHashMap<>();
        for (AgentConfigSnapshot snapshot : snapshots) {
            next.put(snapshot.code(), snapshot);
        }
        this.agents = Collections.unmodifiableMap(next);
    }

    public AgentConfigSnapshot get(String code) {
        return agents.get(code);
    }

    public AgentConfigSnapshot require(String code) {
        AgentConfigSnapshot snapshot = agents.get(code);
        if (snapshot == null) {
            throw new IllegalArgumentException("Agent not found or disabled: " + code);
        }
        return snapshot;
    }

    public Map<String, AgentConfigSnapshot> all() {
        return agents;
    }
}
