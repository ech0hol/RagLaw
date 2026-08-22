package com.raglaw.rag.tool;

public record RagSearchHit(
        String chunkId,
        double score,
        String l1L2L3Path,
        String excerpt
) {
}
