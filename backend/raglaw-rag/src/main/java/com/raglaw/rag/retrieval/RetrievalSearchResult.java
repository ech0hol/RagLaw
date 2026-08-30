package com.raglaw.rag.retrieval;

import com.raglaw.rag.dto.RetrievalHit;
import java.util.List;

public record RetrievalSearchResult(
        List<RetrievalHit> hits,
        int fulltextCount,
        int vectorCount,
        int fulltextAfterFilter,
        int vectorAfterFilter,
        int rrfCount
) {
}
