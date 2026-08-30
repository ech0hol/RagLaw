package com.raglaw.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpDownloadHeadersTest {

    @Test
    void inlineFilename_encodesUnicodeForTomcat() {
        String header = HttpDownloadHeaders.inlineFilename("郑志鹏-华师27届-客户端开发.pdf");
        assertThat(header).contains("filename*=");
        assertThat(header).contains("UTF-8");
    }
}
