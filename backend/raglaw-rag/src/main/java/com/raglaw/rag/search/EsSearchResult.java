package com.raglaw.rag.search;

import com.raglaw.rag.dto.RetrievalHit;
import java.io.IOException;
import java.util.List;

public record EsSearchResult(List<RetrievalHit> hits, boolean failed, IOException error) {

    public static EsSearchResult success(List<RetrievalHit> hits) {
        return new EsSearchResult(hits == null ? List.of() : hits, false, null);
    }

    public static EsSearchResult failure(IOException error) {
        return new EsSearchResult(List.of(), true, error);
    }
}
