package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.rag.dto.RetrievalHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalDiversifierTest {

    private final RetrievalDiversifier diversifier = new RetrievalDiversifier();

    @Test
    void limitsChunksPerDocument() {
        List<RetrievalHit> ranked = List.of(
                hit("c1", "doc-a", 1.0),
                hit("c2", "doc-a", 0.9),
                hit("c3", "doc-a", 0.8),
                hit("c4", "doc-b", 0.7),
                hit("c5", "doc-c", 0.6)
        );

        List<RetrievalHit> diversified = diversifier.diversify(ranked, 5, 2);

        assertThat(diversified).hasSize(4);
        assertThat(diversified.stream().filter(hit -> "doc-a".equals(hit.documentId())).count()).isEqualTo(2);
    }

    private static RetrievalHit hit(String chunkId, String documentId, double score) {
        return new RetrievalHit(chunkId, documentId, "content-" + chunkId, "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR", score);
    }
}
