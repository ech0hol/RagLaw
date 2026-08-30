package com.raglaw.rag.search;

public record ChunkSearchHit(
        String chunkId,
        String documentId,
        String content,
        String l1Path,
        String l2Path,
        String l3Path,
        double score
) {
}
