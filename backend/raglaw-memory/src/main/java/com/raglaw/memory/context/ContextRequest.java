package com.raglaw.memory.context;

import java.util.List;

public record ContextRequest(String profileCode, long memorySnapshotVersion, NodeContextProfile profile,
                             ModelWindow modelWindow, List<ContextItem> items) {
    public ContextRequest {
        if (profile == null || modelWindow == null) throw new IllegalArgumentException("profile/window");
        items = items == null ? List.of() : List.copyOf(items);
    }
}
