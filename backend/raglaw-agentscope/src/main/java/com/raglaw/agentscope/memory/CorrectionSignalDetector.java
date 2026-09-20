package com.raglaw.agentscope.memory;

import java.util.regex.Pattern;

public final class CorrectionSignalDetector {
    private static final Pattern CORRECTION = Pattern.compile(
            "(请记住|记住|刚才.*(说错|不对)|不是.{0,20}是|更正|纠正|改为|应该是)",
            Pattern.CASE_INSENSITIVE);

    private CorrectionSignalDetector() {
    }

    public static boolean isExplicitCorrection(String message) {
        return message != null && CORRECTION.matcher(message).find();
    }
}
