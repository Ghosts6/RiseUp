package com.riseup.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class ResponseParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class ParseError extends RuntimeException {
        public ParseError(String message) {
            super(message);
        }
    }

    public record DecisionDto(String method, String reasoning, Double confidence, String fallback) {}

    public DecisionDto parseDecision(String text, List<String> available) {
        try {
            JsonNode node = MAPPER.readTree(extractJson(text));
            String method = node.path("method").asText(null);
            if (method == null || !available.contains(method)) {
                throw new ParseError("method not in available_methods: " + method);
            }
            Double confidence = node.has("confidence") && !node.get("confidence").isNull()
                    ? node.get("confidence").asDouble()
                    : null;
            String fallback = node.has("fallback") && !node.get("fallback").isNull()
                    ? node.get("fallback").asText()
                    : null;
            if (fallback != null && !available.contains(fallback)) fallback = null;
            return new DecisionDto(method, node.path("reasoning").asText(""), confidence, fallback);
        } catch (ParseError e) {
            throw e;
        } catch (Exception e) {
            throw new ParseError("Invalid JSON decision: " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) throw new ParseError("No JSON object found");
        return text.substring(start, end + 1);
    }
}
