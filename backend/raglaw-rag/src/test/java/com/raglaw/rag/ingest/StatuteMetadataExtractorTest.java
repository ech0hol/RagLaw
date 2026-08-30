package com.raglaw.rag.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatuteMetadataExtractorTest {

    @Test
    void extractFromTitleSuffix() {
        StatuteMetadataExtractor.EffectiveDateResult result = StatuteMetadataExtractor.extract(
                "中华人民共和国危险化学品安全法_20251227",
                ""
        );
        assertThat(result).isNotNull();
        assertThat(result.effectiveDate()).isEqualTo("2025-12-27");
        assertThat(result.effectiveDateSource()).isEqualTo("title");
    }

    @Test
    void extractFromContentPassDate() {
        String text = "2025年12月27日第十四届全国人民代表大会常务委员会第十四次会议通过";
        StatuteMetadataExtractor.EffectiveDateResult result = StatuteMetadataExtractor.extract(
                "某法规",
                text
        );
        assertThat(result).isNotNull();
        assertThat(result.effectiveDate()).isEqualTo("2025-12-27");
        assertThat(result.effectiveDateSource()).isEqualTo("content");
    }

    @Test
    void extractFromContentEffectiveDate() {
        String text = "本法自2026年1月1日起施行。";
        StatuteMetadataExtractor.EffectiveDateResult result = StatuteMetadataExtractor.extract(
                "某法规",
                text
        );
        assertThat(result).isNotNull();
        assertThat(result.effectiveDate()).isEqualTo("2026-01-01");
        assertThat(result.effectiveDateSource()).isEqualTo("content");
    }
}
