package com.raglaw.rag.ingest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StatuteMetadataExtractor {

    private static final Pattern TITLE_SUFFIX_DATE = Pattern.compile("_(\\d{8})$");
    private static final Pattern CONTENT_PASS_DATE = Pattern.compile(
            "(\\d{4})\u5e74(\\d{1,2})\u6708(\\d{1,2})\u65e5[^\\n]{0,120}\u901a\u8fc7"
    );
    private static final Pattern CONTENT_EFFECTIVE_DATE = Pattern.compile(
            "\u81ea(\\d{4})\u5e74(\\d{1,2})\u6708(\\d{1,2})\u65e5\u8d77\u65bd\u884c"
    );

    private StatuteMetadataExtractor() {
    }

    public record EffectiveDateResult(String effectiveDate, String effectiveDateSource) {
    }

    public static EffectiveDateResult extract(String title, String text) {
        EffectiveDateResult fromTitle = extractFromTitle(title);
        if (fromTitle != null) {
            return fromTitle;
        }
        return extractFromContent(text);
    }

    private static EffectiveDateResult extractFromTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        Matcher matcher = TITLE_SUFFIX_DATE.matcher(title.trim());
        if (!matcher.find()) {
            return null;
        }
        String digits = matcher.group(1);
        return new EffectiveDateResult(formatYmd(digits), "title");
    }

    private static EffectiveDateResult extractFromContent(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String sample = text.length() > 3000 ? text.substring(0, 3000) : text;
        Matcher passMatcher = CONTENT_PASS_DATE.matcher(sample);
        if (passMatcher.find()) {
            return new EffectiveDateResult(
                    formatYmd(passMatcher.group(1), passMatcher.group(2), passMatcher.group(3)),
                    "content"
            );
        }
        Matcher effectiveMatcher = CONTENT_EFFECTIVE_DATE.matcher(sample);
        if (effectiveMatcher.find()) {
            return new EffectiveDateResult(
                    formatYmd(effectiveMatcher.group(1), effectiveMatcher.group(2), effectiveMatcher.group(3)),
                    "content"
            );
        }
        return null;
    }

    private static String formatYmd(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) {
            return null;
        }
        return yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-" + yyyymmdd.substring(6, 8);
    }

    private static String formatYmd(String year, String month, String day) {
        int y = Integer.parseInt(year);
        int m = Integer.parseInt(month);
        int d = Integer.parseInt(day);
        return String.format("%04d-%02d-%02d", y, m, d);
    }
}
