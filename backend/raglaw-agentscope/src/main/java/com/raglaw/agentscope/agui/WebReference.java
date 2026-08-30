package com.raglaw.agentscope.agui;

public record WebReference(
        int index,
        String chunkId,
        String title,
        String url,
        String excerpt
) {
}
