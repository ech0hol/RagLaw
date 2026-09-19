package com.raglaw.agentscope.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.raglaw.agentscope.agui.DashScopeClient;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class TaskClassifierLlm implements TaskClassifier {
    private static final Logger log = LoggerFactory.getLogger(TaskClassifierLlm.class);
    private final DashScopeClient client;
    private final ObjectMapper mapper;
    private final AgentscopeLlmProperties properties;
    private final Environment environment;

    public TaskClassifierLlm(DashScopeClient client, ObjectMapper mapper,
            AgentscopeLlmProperties properties, Environment environment) {
        this.client = client;
        this.mapper = mapper;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public TaskClassification classify(RoutingRequest request) {
        if (request == null) {
            return fallback("invalid_request");
        }
        if (properties.isMock() || environment.matchesProfiles("test")) {
            return fallback("classifier_disabled");
        }
        String apiKey = environment.getProperty("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return fallback("model_unavailable");
        }
        try {
            String raw = client.completeChat(apiKey, properties.getTaskClassifierModel(),
                    TaskClassifierPrompt.systemPrompt(), TaskClassifierPrompt.userMessage(request));
            return parse(raw);
        } catch (Exception ex) {
            log.warn("Task classifier failed: {}", safeReason(ex));
            return fallback(safeReason(ex));
        }
    }

    TaskClassification parse(String raw) throws Exception {
        String json = unwrapFence(raw);
        if (json.isBlank()) {
            throw new IllegalArgumentException("empty_output");
        }
        JsonParser parser = mapper.getFactory().createParser(json);
        JsonNode root = mapper.readTree(parser);
        if (parser.nextToken() != null) {
            throw new IllegalArgumentException("trailing_output");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("not_object");
        }
        Set<String> allowed = Set.of("taskType", "riskSignals", "confidence", "missingMaterials", "rationale");
        var fields = root.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw new IllegalArgumentException("unknown_field");
            }
        }
        require(root, "taskType", JsonNode::isTextual);
        require(root, "riskSignals", JsonNode::isArray);
        require(root, "confidence", JsonNode::isNumber);
        require(root, "missingMaterials", JsonNode::isArray);
        require(root, "rationale", JsonNode::isTextual);
        TaskType taskType = TaskType.valueOf(root.get("taskType").textValue());
        JsonNode signalsNode = root.get("riskSignals");
        Set<RiskSignal> signals = new HashSet<>();
        for (JsonNode value : signalsNode) {
            if (!value.isTextual() || !signals.add(RiskSignal.valueOf(value.textValue()))) {
                throw new IllegalArgumentException("invalid_or_duplicate_risk_signal");
            }
        }
        double confidence = root.get("confidence").doubleValue();
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("invalid_confidence");
        }
        List<String> missing = strings(root.get("missingMaterials"), "missingMaterials");
        String rationale = root.get("rationale").textValue();
        return new TaskClassification(taskType, signals, confidence, missing, rationale,
                properties.getTaskClassifierPromptVersion(), properties.getTaskClassifierModel());
    }

    private static void require(JsonNode root, String field, java.util.function.Predicate<JsonNode> test) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !test.test(node)) {
            throw new IllegalArgumentException("missing_or_invalid_" + field);
        }
    }

    private static List<String> strings(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException("invalid_" + field);
            }
            values.add(item.textValue());
        }
        return values;
    }

    private static String unwrapFence(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.startsWith("```") && value.endsWith("```")) {
            int newline = value.indexOf('\n');
            if (newline < 0) throw new IllegalArgumentException("invalid_markdown_fence");
            value = value.substring(newline + 1, value.length() - 3).trim();
        }
        return value;
    }

    private TaskClassification fallback(String reason) {
        return new TaskClassification(TaskType.DISPUTE_ANALYSIS, Set.of(RiskSignal.MISSING_CORE_MATERIAL),
                0.0, List.of("classifier_output"), "classifier_failure:" + reason,
                properties.getTaskClassifierPromptVersion(), properties.getTaskClassifierModel());
    }

    private static String safeReason(Exception ex) {
        String name = ex.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("timeout")) return "timeout";
        if (ex instanceof IllegalArgumentException) return "invalid_output";
        return "model_error";
    }
}
