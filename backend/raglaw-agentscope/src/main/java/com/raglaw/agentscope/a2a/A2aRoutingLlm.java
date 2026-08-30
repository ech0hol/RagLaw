package com.raglaw.agentscope.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.agui.DashScopeClient;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class A2aRoutingLlm {

    private static final Logger log = LoggerFactory.getLogger(A2aRoutingLlm.class);
    private static final String ROUTING_MODEL = "qwen-turbo";

    private final DashScopeClient dashScopeClient;
    private final ObjectMapper objectMapper;
    private final AgentscopeLlmProperties llmProperties;
    private final Environment environment;

    public A2aRoutingLlm(
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

    public Optional<String> pickPeer(List<String> peers, String query) {
        if (peers == null || peers.isEmpty()) {
            return Optional.empty();
        }
        if (llmProperties.isMock() || environment.matchesProfiles("test")) {
            return Optional.empty();
        }
        String apiKey = environment.getProperty("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        String peerList = peers.stream().map(code -> "- " + code).collect(Collectors.joining("\n"));
        String systemPrompt = """
                你是法律多 Agent 路由助手。根据用户问题，从候选专家 code 中选择一个最合适的。
                只输出 JSON：{"peerCode":"CODE"}，不要输出其它文字。
                """;
        String userMessage = "候选专家：\n" + peerList + "\n\n用户问题：" + query;

        try {
            String raw = dashScopeClient.completeChat(apiKey, ROUTING_MODEL, systemPrompt, userMessage);
            JsonNode root = objectMapper.readTree(extractJson(raw));
            String peerCode = root.path("peerCode").asText(null);
            if (peerCode != null && peers.contains(peerCode)) {
                return Optional.of(peerCode);
            }
        } catch (Exception ex) {
            log.warn("A2A routing LLM failed: {}", ex.getMessage());
        }
        return Optional.empty();
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
}
