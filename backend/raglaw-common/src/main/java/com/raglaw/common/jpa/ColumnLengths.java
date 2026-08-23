package com.raglaw.common.jpa;

public final class ColumnLengths {

    public static final int UUID = 36;
    public static final int AGENT_CODE = 64;
    public static final int CATEGORY_CODE = 64;
    public static final int DOC_TYPE = 32;
    public static final int ROLE = 32;
    public static final int DISPLAY_NAME = 128;
    public static final int TITLE = 512;
    public static final int CATEGORY_PATH = 512;
    public static final int CATEGORY_NAME = 128;
    public static final int CHUNK_PATH = 128;
    public static final int TRACE_STAGE = 64;
    public static final int MODEL = 128;
    public static final int LANGFUSE_TRACE_ID = 128;
    public static final int MINIO_KEY = 512;
    public static final int REJECT_REASON = 512;

    private ColumnLengths() {
    }
}
