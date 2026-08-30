package com.raglaw.rag.ingest;

import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.DocumentChunkEntity;
import java.util.regex.Pattern;

public final class DisplayChunkFilter {

    private static final Pattern VIRTUAL_PARENT_LABEL = Pattern.compile("^段落组\\s*\\d+$");

    private DisplayChunkFilter() {
    }

    public static boolean includeInFullText(DocumentChunkEntity chunk) {
        if (chunk == null) {
            return false;
        }
        if (chunk.getChunkLevel() == ChunkLevel.PARENT) {
            return false;
        }
        String content = chunk.getContent();
        if (content == null || content.isBlank()) {
            return false;
        }
        return !VIRTUAL_PARENT_LABEL.matcher(content.trim()).matches();
    }

    public static boolean includeInFullText(String content, String chunkLevel) {
        if (content == null || content.isBlank()) {
            return false;
        }
        if ("PARENT".equals(chunkLevel)) {
            return false;
        }
        return !VIRTUAL_PARENT_LABEL.matcher(content.trim()).matches();
    }
}
