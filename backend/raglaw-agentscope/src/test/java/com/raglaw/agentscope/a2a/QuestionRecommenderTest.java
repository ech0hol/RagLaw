package com.raglaw.agentscope.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.agui.DashScopeClient;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class QuestionRecommenderTest {

    @Mock
    private DashScopeClient dashScopeClient;

    @Mock
    private Environment environment;

    private AgentscopeLlmProperties llmProperties;
    private QuestionRecommender recommender;

    @BeforeEach
    void setUp() {
        llmProperties = new AgentscopeLlmProperties();
        recommender = new QuestionRecommender(
                dashScopeClient,
                new ObjectMapper(),
                llmProperties,
                environment
        );
    }

    @Test
    void fallsBackWhenMockModeEnabled() {
        llmProperties.setMock(true);

        List<String> questions = recommender.recommend(
                "通用语言文字有哪些",
                "回答关于语言文字法",
                "GENERAL",
                3
        );

        assertThat(questions).isNotEmpty();
        assertThat(questions).contains("相关法规的适用条件是什么？");
    }

    @Test
    void fallsBackWhenApiKeyMissing() {
        when(environment.matchesProfiles("test")).thenReturn(false);
        when(environment.getProperty("DASHSCOPE_API_KEY")).thenReturn("");

        List<String> questions = recommender.recommend(
                "通用语言文字有哪些",
                "回答关于语言文字法",
                "GENERAL",
                3
        );

        assertThat(questions).isNotEmpty();
    }

    @Test
    void parsesLlmJsonResponse() throws Exception {
        llmProperties.setMock(false);
        when(environment.matchesProfiles("test")).thenReturn(false);
        when(environment.getProperty("DASHSCOPE_API_KEY")).thenReturn("test-key");
        when(dashScopeClient.completeChat(
                eq("test-key"),
                eq("qwen-turbo"),
                anyString(),
                anyString()
        )).thenReturn("{\"questions\":[\"规范汉字包含哪些内容？\",\"哪些领域必须使用规范汉字？\",\"违反规定有何责任？\"]}");

        List<String> questions = recommender.recommend(
                "通用语言文字有哪些",
                "国家推广普通话和规范汉字。",
                "STATUTE_CIVIL",
                3
        );

        assertThat(questions).containsExactly(
                "规范汉字包含哪些内容？",
                "哪些领域必须使用规范汉字？",
                "违反规定有何责任？"
        );
    }

    @Test
    void fallsBackWhenLlmReturnsInvalidJson() throws Exception {
        llmProperties.setMock(false);
        when(environment.matchesProfiles("test")).thenReturn(false);
        when(environment.getProperty("DASHSCOPE_API_KEY")).thenReturn("test-key");
        when(dashScopeClient.completeChat(
                eq("test-key"),
                eq("qwen-turbo"),
                anyString(),
                anyString()
        )).thenReturn("not json");

        List<String> questions = recommender.recommend(
                "通用语言文字有哪些",
                "国家推广普通话和规范汉字。",
                "GENERAL",
                3
        );

        assertThat(questions).contains("相关法规的适用条件是什么？");
    }

    @Test
    void buildUserPromptIncludesQuestionAndTruncatedAnswer() {
        String longAnswer = "A".repeat(2500);
        String prompt = QuestionRecommender.buildUserPrompt("用户问题", longAnswer, "GENERAL");

        assertThat(prompt).contains("用户问题：");
        assertThat(prompt).contains("用户问题");
        assertThat(prompt).contains("助手回答：");
        assertThat(prompt).contains("…");
        assertThat(prompt.length()).isLessThan(longAnswer.length());
    }
}
