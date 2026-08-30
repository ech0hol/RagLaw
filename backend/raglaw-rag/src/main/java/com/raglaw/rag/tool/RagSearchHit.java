package com.raglaw.rag.tool;

public record RagSearchHit(
        String chunkId,
        String documentId,
        double score,
        String l1L2L3Path,
        String excerpt,
        String llmContent,
        String title,
        String matchedChunkId
) {
    public RagSearchHit(String chunkId, String documentId, double score, String l1L2L3Path, String excerpt) {
        this(chunkId, documentId, score, l1L2L3Path, excerpt, excerpt, null, chunkId);
    }

    public RagSearchHit(
            String chunkId,
            String documentId,
            double score,
            String l1L2L3Path,
            String excerpt,
            String llmContent,
            String title
    ) {
        this(chunkId, documentId, score, l1L2L3Path, excerpt, llmContent, title, chunkId);
    }

    public String llmContentOrExcerpt() {
        if (llmContent != null && !llmContent.isBlank()) {
            return llmContent;
        }
        return excerpt;
    }
}
