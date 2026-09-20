package com.raglaw.agentscope.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.agui.DashScopeClient;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.port.MemoryCandidateExtractor;
import com.raglaw.memory.service.MemoryCandidate;
import com.raglaw.memory.domain.MemorySourceType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class LlmMemoryCandidateExtractor implements MemoryCandidateExtractor {
    private static final Logger log = LoggerFactory.getLogger(LlmMemoryCandidateExtractor.class);
    private static final Set<String> KNOWN_PREDICATES = Set.of(
            "NAME", "GENDER", "MONTHLY_SALARY", "TAX_BASIS", "EMPLOYMENT_STATUS",
            "EMPLOYMENT_START_DATE", "EMPLOYMENT_END_DATE", "CONTRACT_PARTY", "CONTRACT_TERM",
            "REVIEW_POSITION", "STATED_CONCERN", "CLAIM_AMOUNT", "EVENT_DATE", "ADDRESS",
            "CONTACT", "ROLE", "WORK_LOCATION"
    );

    private final DashScopeClient dashScopeClient;
    private final ObjectMapper objectMapper;
    private final AgentscopeLlmProperties llmProperties;
    private final Environment environment;

    public LlmMemoryCandidateExtractor(DashScopeClient dashScopeClient, ObjectMapper objectMapper,
                                       AgentscopeLlmProperties llmProperties, Environment environment) {
        this.dashScopeClient = dashScopeClient;
        this.objectMapper = objectMapper;
        this.llmProperties = llmProperties;
        this.environment = environment;
    }

    @Override
    public List<MemoryCandidate> extract(CaseScope scope, String sourceMessageId, String messageText) {
        if (scope == null || blank(sourceMessageId) || blank(messageText)) return List.of();
        if (llmProperties.isMock() || environment.matchesProfiles("test")) return List.of();
        String apiKey = environment.getProperty("DASHSCOPE_API_KEY");
        if (blank(apiKey)) return List.of();
        try {
            String raw = dashScopeClient.completeChat(apiKey, llmProperties.getTaskClassifierModel(),
                    MemoryExtractionPrompt.SYSTEM, messageText);
            return parseResponse(raw, sourceMessageId);
        } catch (Exception exception) {
            log.warn("Memory candidate extraction failed sourceId={}: {}", sourceMessageId, exception.getMessage());
            return List.of();
        }
    }

    List<MemoryCandidate> parseResponse(String raw, String sourceMessageId) {
        if (blank(raw) || blank(sourceMessageId)) return List.of();
        try {
            JsonNode root = objectMapper.readTree(extractJson(raw));
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray()) return List.of();
            List<MemoryCandidate> result = new ArrayList<>();
            for (JsonNode node : candidates) {
                String subjectType = text(node, "subjectType");
                String subjectId = text(node, "subjectId");
                String predicate = text(node, "predicate");
                JsonNode value = node.get("value");
                if (blank(subjectType) || blank(subjectId) || blank(predicate) || value == null
                        || value.isNull() || !KNOWN_PREDICATES.contains(predicate.toUpperCase())) continue;
                Instant validFrom = parseInstant(text(node, "validFrom"));
                Instant validTo = parseInstant(text(node, "validTo"));
                result.add(new MemoryCandidate(subjectType, subjectId, predicate.toUpperCase(),
                        objectMapper.writeValueAsString(value), validFrom, validTo,
                        MemorySourceType.USER_MESSAGE, sourceMessageId,
                        node.path("explicitCorrection").asBoolean(false),
                        node.path("negated").asBoolean(false)));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            log.warn("Memory candidate JSON invalid: {}", exception.getMessage());
            return List.of();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private static Instant parseInstant(String value) {
        if (blank(value)) return null;
        try { return Instant.parse(value); } catch (Exception ignored) { return null; }
    }

    private static String extractJson(String raw) {
        String value = raw.strip();
        if (value.startsWith("```") && value.endsWith("```")) {
            int newline = value.indexOf('\n');
            value = newline >= 0 ? value.substring(newline + 1, value.length() - 3).strip() : value;
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : value;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
