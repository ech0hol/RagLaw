package com.raglaw.agentscope.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TavilyResultParser {

    private final ObjectMapper objectMapper;

    public TavilyResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ParsedWebHit> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(raw));
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                results = root.path("data").path("results");
            }
            if (!results.isArray()) {
                return List.of();
            }
            List<ParsedWebHit> hits = new ArrayList<>();
            for (JsonNode item : results) {
                String title = text(item, "title");
                String url = text(item, "url");
                String content = firstNonBlank(text(item, "content"), text(item, "snippet"));
                if (url.isBlank()) {
                    continue;
                }
                hits.add(new ParsedWebHit(
                        title.isBlank() ? url : title,
                        url,
                        content
                ));
            }
            return hits;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (!first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw.trim();
    }

    public record ParsedWebHit(String title, String url, String excerpt) {
    }
}
