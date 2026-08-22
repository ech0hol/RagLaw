package com.raglaw.rag.dto;

public record RetrievalHit(
        String chunkId,
        String documentId,
        String content,
        String l1Path,
        String l2Path,
        String l3Path,
        double score
) {

    public RetrievalHit withScore(double newScore) {
        return new RetrievalHit(chunkId, documentId, content, l1Path, l2Path, l3Path, newScore);
    }

    public String l1L2L3Path() {
        return l1Path + "/" + l2Path + "/" + l3Path;
    }

    public String excerpt() {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.length() > 240 ? content.substring(0, 240) + "…" : content;
    }
}
