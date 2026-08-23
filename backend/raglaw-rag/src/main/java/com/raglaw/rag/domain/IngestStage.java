package com.raglaw.rag.domain;

public final class IngestStage {

    public static final String PENDING = "PENDING";
    public static final String PARSING = "PARSING";
    public static final String PARSED = "PARSED";
    public static final String INDEXING = "INDEXING";
    public static final String INDEXED = "INDEXED";
    public static final String FAILED = "FAILED";

    private IngestStage() {
    }
}
