package com.raglaw.agentscope.agui;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.rag.tool.RagSearchHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagAnswerContextBuilderTest {

    @Test
    void buildUsesLlmContentAndAnswerConstraints() {
        String longContent = "第".repeat(500);
        RagSearchHit hit = new RagSearchHit(
                "c1",
                "d1",
                0.9,
                "/STATUTE/CIVIL/LABOR",
                "short",
                longContent,
                "劳动合同法"
        );

        String context = RagAnswerContextBuilder.build(List.of(hit), "民法商法", false);

        assertThat(context).contains(longContent);
        assertThat(context).contains("首段：一句话直接总结");
        assertThat(context).contains("《劳动合同法》");
        assertThat(context).doesNotContain("本回复仅供参考");
        assertThat(context).contains("**《法规名》第X条**[n]");
        assertThat(context).contains("1. **目录内化学品需许可**");
    }

    @Test
    void buildAddsKnowledgeBoundaryWhenLowConfidence() {
        String context = RagAnswerContextBuilder.build(
                List.of(new RagSearchHit("c1", "d1", 0.5, "/STATUTE", "excerpt")),
                "民法商法",
                true
        );

        assertThat(context).contains("可能与问题不完全匹配");
    }

    @Test
    void postProcessFixesBrokenBold() {
        String processed = RagAnswerContextBuilder.postProcess("是否属于 **\n\n《危险化学品安全管理条例》\n** 所定义");

        assertThat(processed).contains("**《危险化学品安全管理条例》**");
        assertThat(processed).doesNotContain("本回复仅供参考");
    }

    @Test
    void postProcessStripsRetrievalStatusPrefix() {
        String processed = RagAnswerContextBuilder.postProcess(
                "正在检索刑法中关于刑罚种类的法规依据……\n\n刑法规定的刑罚分为主刑和附加刑。"
        );

        assertThat(processed).doesNotContain("正在检索");
        assertThat(processed).startsWith("刑法规定的刑罚分为主刑和附加刑。");
    }

    @Test
    void postProcessMergesLineLeadingPunctuation() {
        String processed = RagAnswerContextBuilder.postProcess("第一段结束\n，第二段\n。第三段");

        assertThat(processed).isEqualTo("第一段结束，第二段。第三段");
    }

    @Test
    void postProcessReturnsEmptyForBlankInput() {
        assertThat(RagAnswerContextBuilder.postProcess("")).isEmpty();
        assertThat(RagAnswerContextBuilder.postProcess(null)).isEmpty();
    }

    @Test
    void postProcessPreservesParagraphBreaks() {
        String processed = RagAnswerContextBuilder.postProcess("段落A\n\n段落B");

        assertThat(processed).startsWith("段落A\n\n段落B");
    }

    @Test
    void postProcessInsertsParagraphBeforeDiscourseMarkers() {
        String processed = RagAnswerContextBuilder.postProcess("结论。首先说明");

        assertThat(processed).startsWith("结论。\n\n首先说明");
    }

    @Test
    void postProcessPreservesCitationFootnotesAndStripsOrphans() {
        String processed = RagAnswerContextBuilder.postProcess(
                "依据 **《条例》第三条**[5] 规定，另见上文[3]说明。"
        );

        assertThat(processed).contains("**《条例》第三条**[5]");
        assertThat(processed).doesNotContain("[3]");
    }

    @Test
    void postProcessPreservesNumberedSectionBreaks() {
        String processed = RagAnswerContextBuilder.postProcess("…明确体现。\n\n1. **注册条件**");

        assertThat(processed).startsWith("…明确体现。\n\n1. **注册条件**");
    }

    @Test
    void postProcessInsertsBreakBeforeNumberedSection() {
        String processed = RagAnswerContextBuilder.postProcess("…明确体现。1. **注册条件**");

        assertThat(processed).startsWith("…明确体现。\n\n1. **注册条件**");
    }

    @Test
    void postProcessPreservesNewlineBeforeChineseSectionHeader() {
        String processed = RagAnswerContextBuilder.postProcess("指普通话和规范汉字。\n**一、法律定义**");

        assertThat(processed).startsWith("指普通话和规范汉字。\n\n**一、法律定义**");
        assertThat(processed).doesNotContain("汉字。**一、");
    }

    @Test
    void postProcessInsertsBreakBeforeInlineChineseSectionHeader() {
        String processed = RagAnswerContextBuilder.postProcess("指普通话和规范汉字。**一、法律定义**");

        assertThat(processed).startsWith("指普通话和规范汉字。\n\n**一、法律定义**");
    }

    @Test
    void postProcessInsertsBreakBeforeInlineChineseSectionHeaderInBulletContext() {
        String processed = RagAnswerContextBuilder.postProcess("及未被收录的旧字形。**三、适用范围**");

        assertThat(processed).startsWith("及未被收录的旧字形。\n\n**三、适用范围**");
    }

    @Test
    void postProcessInsertsBreakBeforeListItemAfterNewline() {
        String processed = RagAnswerContextBuilder.postProcess("国家通用共同语。\n- **规范汉字**：定义");

        assertThat(processed).startsWith("国家通用共同语。\n\n- **规范汉字**：定义");
        assertThat(processed).doesNotContain("共同语。-");
    }

    @Test
    void postProcessInsertsBreakBeforeInlineListItem() {
        String processed = RagAnswerContextBuilder.postProcess("国家通用共同语。- **规范汉字**：定义");

        assertThat(processed).startsWith("国家通用共同语。\n\n- **规范汉字**：定义");
    }

    @Test
    void postProcessPreservesNestedListAfterNumberedSectionHeading() {
        String input = """
                1. **主刑的种类与适用规则**
                - 管制：依据 **《中华人民共和国刑法》第38条**[1]
                - 有期徒刑：依据 **《中华人民共和国刑法》第42条**[2]
                - 无期徒刑：依据 **《中华人民共和国刑法》第45条**[3]
                """;
        String processed = RagAnswerContextBuilder.postProcess(input);

        assertThat(processed).contains("1. **主刑的种类与适用规则**\n   - 管制");
        assertThat(processed).contains("\n   - 有期徒刑");
        assertThat(processed).contains("\n   - 无期徒刑");
        assertThat(processed).doesNotContain("； - 有期徒刑");
    }

    @Test
    void postProcessPreservesListItemWhenNewlineHasSpaceBeforeDash() {
        String input = "说明如下：\n - 有期徒刑：依据 **《刑法》第42条**[2]";
        String processed = RagAnswerContextBuilder.postProcess(input);

        assertThat(processed).contains("\n - 有期徒刑");
        assertThat(processed).doesNotContain("如下： - 有期徒刑");
    }

    @Test
    void postProcessStripsContradictoryRetrievalDisclaimerWhenCitationsPresent() {
        String input = """
                刑法规定的刑罚分为主刑和附加刑。依据 **《中华人民共和国刑法》第三十三条**[1]。

                当前知识库未检索到与「刑法中刑罚种类」直接对应的其他法规或司法解释片段。
                """;
        String processed = RagAnswerContextBuilder.postProcess(input);

        assertThat(processed).contains("**《中华人民共和国刑法》第三十三条**[1]");
        assertThat(processed).doesNotContain("未检索到");
    }

    @Test
    void postProcessStripsContradictoryRetrievalDisclaimerWhenRetrievalEvidenceExists() {
        String input = """
                正文内容。

                知识库中未检索到与问题直接对应的法规片段。
                """;
        String processed = RagAnswerContextBuilder.postProcess(input, true);

        assertThat(processed).contains("正文内容");
        assertThat(processed).doesNotContain("未检索到");
    }

    @Test
    void postProcessDedupesRepeatedAnswerBlocks() {
        String input = """
                可以异地报销。

                1. **条件**
                - 要点一

                3. **流程**
                - 要点三

                可以异地报销。

                1. **备案要求**
                - 先备案

                2. **报销流程**
                - 再结算
                """;
        String processed = RagAnswerContextBuilder.postProcess(input);

        assertThat(processed).contains("1. **备案要求**");
        assertThat(processed).contains("2. **报销流程**");
        assertThat(processed).doesNotContain("1. **条件**");
    }

    @Test
    void postProcessDedupesInterleavedRepeatedBlocksWithoutDuplicateIntro() {
        String input = """
                城乡居民医保可以异地报销，需满足备案等条件。

                1. **异地就医前提：办理备案登记**
                - 须事先备案

                2. **定点医疗机构范围**
                - 选定点机构

                3. **直接结算与手工报销**
                - 支持直接结算

                1. **必须事先办理异地就医备案**
                - 线上或线下备案

                2. **报销流程**
                - 持票回参保地报销
                """;
        String processed = RagAnswerContextBuilder.postProcess(input);

        assertThat(processed).contains("1. **必须事先办理异地就医备案**");
        assertThat(processed).contains("2. **报销流程**");
        assertThat(processed).doesNotContain("1. **异地就医前提：办理备案登记**");
        assertThat(processed).doesNotContain("3. **直接结算与手工报销**");
    }
}
