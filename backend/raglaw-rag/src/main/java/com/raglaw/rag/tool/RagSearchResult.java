package com.raglaw.rag.tool;

import com.raglaw.rag.retrieval.funnel.FunnelResult;
import java.util.List;
import java.util.Map;

public record RagSearchResult(
        List<RagSearchHit> hits,
        FunnelResult funnelResult,
        Map<String, Object> retrievalTrace
) {
}
