package com.raglaw.rag.contract;

public final class ContractReviewStatus {

    public static final String NOT_RUN = "NOT_RUN";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED_NO_CHUNKS = "SKIPPED_NO_CHUNKS";

    private ContractReviewStatus() {
    }
}
