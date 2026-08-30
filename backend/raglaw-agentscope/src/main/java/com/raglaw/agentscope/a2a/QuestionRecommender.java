package com.raglaw.agentscope.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.agui.DashScopeClient;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class QuestionRecommender {

    private static final Logger log = LoggerFactory.getLogger(QuestionRecommender.class);
    private static final String RECOMMEND_MODEL = "qwen-turbo";
    private static final int MAX_ANSWER_CHARS = 2000;
    private static final int MAX_QUESTION_CHARS = 80;

    private static final List<String> DEFAULT_FOLLOW_UPS = List.of(
            "相关法规的适用条件是什么？",
            "类似案例的裁判要点有哪些？",
            "如需起诉应准备哪些材料？"
    );

    private final DashScopeClient dashScopeClient;
    private final ObjectMapper objectMapper;
    private final AgentscopeLlmProperties llmProperties;
    private final Environment environment;

    public QuestionRecommender(
            DashScopeClient dashScopeClient,
            ObjectMapper objectMapper,
            AgentscopeLlmProperties llmProperties,
            Environment environment
    ) {
        this.dashScopeClient = dashScopeClient;
        this.objectMapper = objectMapper;
        this.llmProperties = llmProperties;
        this.environment = environment;
    }

    public List<String> recommend(String userMessage, String assistantAnswer, String agentCode, int limit) {
        int effectiveLimit = Math.max(1, limit);
        if (shouldUseFallback()) {
            return recommendLegacy(userMessage, effectiveLimit);
        }
        try {
            List<String> fromLlm = recommendFromLlm(userMessage, assistantAnswer, agentCode, effectiveLimit);
            if (!fromLlm.isEmpty()) {
                return fromLlm;
            }
        } catch (Exception ex) {
            log.warn("Question recommendation LLM failed: {}", ex.getMessage());
        }
        return recommendLegacy(userMessage, effectiveLimit);
    }

    private boolean shouldUseFallback() {
        return llmProperties.isMock()
                || environment.matchesProfiles("test")
                || environment.getProperty("DASHSCOPE_API_KEY") == null
                || environment.getProperty("DASHSCOPE_API_KEY").isBlank();
    }

    private List<String> recommendFromLlm(
            String userMessage,
            String assistantAnswer,
            String agentCode,
            int limit
    ) throws Exception {
        String apiKey = environment.getProperty("DASHSCOPE_API_KEY");
        String systemPrompt = """
                你是法律对话助手。根据用户问题与助手回答，生成后续追问建议。
                要求：
                - 生成 %d 条简短中文追问，每条可独立作为用户下一条消息
                - 须紧扣当前话题，帮助用户深入理解或延展实务问题
                - 不要重复助手已充分回答的内容
                - 每条不超过 40 字，带问号结尾
                只输出 JSON：{"questions":["问题1","问题2","问题3"]}，不要输出其它文字。
                """.formatted(limit).trim();
        String userPrompt = buildUserPrompt(userMessage, assistantAnswer, agentCode);
        String raw = dashScopeClient.completeChat(apiKey, RECOMMEND_MODEL, systemPrompt, userPrompt);
        return parseQuestions(raw, limit);
    }

    static String buildUserPrompt(String userMessage, String assistantAnswer, String agentCode) {
        String question = userMessage == null ? "" : userMessage.trim();
        String answer = truncate(assistantAnswer == null ? "" : assistantAnswer.trim(), MAX_ANSWER_CHARS);
        String agent = agentCode == null || agentCode.isBlank() ? "GENERAL" : agentCode.trim();
        return "专家上下文：" + agent + "\n\n用户问题：\n" + question + "\n\n助手回答：\n" + answer;
    }

    private List<String> parseQuestions(String raw, int limit) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(raw));
        JsonNode questionsNode = root.path("questions");
        if (!questionsNode.isArray()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (JsonNode node : questionsNode) {
            String question = normalizeQuestion(node.asText(""));
            if (!question.isBlank()) {
                unique.add(question);
            }
        }
        List<String> result = new ArrayList<>();
        for (String question : unique) {
            if (result.size() >= limit) {
                break;
            }
            result.add(question);
        }
        return result;
    }

    private static String normalizeQuestion(String question) {
        String trimmed = question == null ? "" : question.trim();
        if (trimmed.length() > MAX_QUESTION_CHARS) {
            trimmed = trimmed.substring(0, MAX_QUESTION_CHARS).trim();
        }
        return trimmed;
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars).trim() + "…";
    }

    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw.trim();
    }

    private List<String> recommendLegacy(String userMessage, int limit) {
        if (userMessage == null || userMessage.isBlank()) {
            return DEFAULT_FOLLOW_UPS.stream().limit(limit).toList();
        }
        if (userMessage.contains("工资") || userMessage.contains("劳动")) {
            return List.of(
                    "拖欠工资可以主张哪些赔偿？",
                    "劳动仲裁的时效是多久？",
                    "如何固定欠薪证据？"
            ).stream().limit(limit).toList();
        }
        if (userMessage.contains("合同") || userMessage.contains("违约")) {
            return List.of(
                    "违约金过高能否请求调减？",
                    "解除合同需要满足哪些条件？",
                    "对方违约时如何保全证据？"
            ).stream().limit(limit).toList();
        }
        return DEFAULT_FOLLOW_UPS.stream().limit(limit).toList();
    }
}
