package com.raglaw.rag.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RevisionCalculatorTest {

    @Test
    void replacesExcerptWithSuggestion() {
        String out = RevisionCalculator.compute(
                "甲方应支付违约金十万元。",
                "十万元",
                "五万元"
        );
        assertEquals("甲方应支付违约金五万元。", out);
    }

    @Test
    void appendsSuggestionWhenExcerptMissing() {
        String out = RevisionCalculator.compute("原文。", "不存在", "建议补充");
        assertTrue(out.contains("【建议】建议补充"));
    }
}
