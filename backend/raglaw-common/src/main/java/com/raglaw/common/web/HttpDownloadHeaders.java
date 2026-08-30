package com.raglaw.common.web;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;

public final class HttpDownloadHeaders {

    private HttpDownloadHeaders() {
    }

    public static String inlineFilename(String filename) {
        return ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    public static String attachmentFilename(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
