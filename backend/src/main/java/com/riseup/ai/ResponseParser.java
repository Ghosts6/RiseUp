package com.riseup.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ResponseParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> PRIORITIES = Set.of("low", "medium", "high", "critical");
    private static final Set<String> CATEGORIES = Set.of("work", "personal", "health", "other");

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

    /** Normalize LLM / mock output into the MockNlp reminder envelope. */
    public Map<String, Object> parseReminder(String text) {
        try {
            JsonNode node = MAPPER.readTree(extractJson(text));
            if (node.has("status")) {
                return normalizeEnvelope(node);
            }
            boolean needs = node.path("needs_clarification").asBoolean(false)
                    || node.path("needsClarification").asBoolean(false);
            if (needs) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("status", "needs_clarification");
                out.put(
                        "question",
                        firstNonBlank(
                                node.path("clarification_question").asText(null),
                                node.path("question").asText(null),
                                "Could you clarify the reminder?"));
                out.put("draft", null);
                return out;
            }
            Map<String, Object> draft = draftFromNode(node.has("draft") ? node.get("draft") : node);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "ok");
            out.put("draft", draft);
            return out;
        } catch (ParseError e) {
            throw e;
        } catch (Exception e) {
            throw new ParseError("Invalid reminder JSON: " + e.getMessage());
        }
    }

    public Map<String, Object> parsePrioritize(String text, List<Integer> expectedIds) {
        try {
            JsonNode node = MAPPER.readTree(extractJson(text));
            List<Integer> order = new ArrayList<>();
            JsonNode arr = node.path("order");
            if (!arr.isArray()) {
                throw new ParseError("order must be an array");
            }
            for (JsonNode n : arr) {
                order.add(n.asInt());
            }
            if (order.size() != expectedIds.size() || !order.containsAll(expectedIds)) {
                throw new ParseError("order must be a permutation of reminder ids");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("order", order);
            out.put("reasoning", node.path("reasoning").asText(""));
            return out;
        } catch (ParseError e) {
            throw e;
        } catch (Exception e) {
            throw new ParseError("Invalid prioritize JSON: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> parseRecommendations(String text) {
        try {
            JsonNode node = MAPPER.readTree(extractJson(text));
            JsonNode items = node.path("items");
            if (!items.isArray()) {
                throw new ParseError("items must be an array");
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", item.path("type").asText("general"));
                row.put("title", item.path("title").asText("Suggestion"));
                row.put("rationale", item.path("rationale").asText(""));
                Map<String, Object> change = new LinkedHashMap<>();
                JsonNode changeNode = item.path("change");
                if (changeNode.isObject()) {
                    changeNode.fields().forEachRemaining(e -> change.put(e.getKey(), jsonValue(e.getValue())));
                }
                row.put("change", change);
                out.add(row);
            }
            return out;
        } catch (ParseError e) {
            throw e;
        } catch (Exception e) {
            throw new ParseError("Invalid recommendations JSON: " + e.getMessage());
        }
    }

    public String extractJson(String text) {
        if (text == null) {
            throw new ParseError("No JSON object found");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new ParseError("No JSON object found");
        }
        return text.substring(start, end + 1);
    }

    private Map<String, Object> normalizeEnvelope(JsonNode node) {
        String status = node.path("status").asText("ok");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        if (node.has("question") && !node.get("question").isNull()) {
            out.put("question", node.path("question").asText());
        }
        if (node.has("draft") && !node.get("draft").isNull()) {
            out.put("draft", draftFromNode(node.get("draft")));
        } else {
            out.put("draft", null);
        }
        return out;
    }

    private Map<String, Object> draftFromNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new ParseError("draft missing");
        }
        String title = node.path("title").asText("").strip();
        if (title.isBlank()) {
            throw new ParseError("title required");
        }
        String due = firstNonBlank(
                node.path("due").asText(null),
                node.path("due_at").asText(null),
                node.path("due_local").asText(null));
        if (due == null || due.isBlank()) {
            throw new ParseError("due required");
        }
        String priority = node.path("priority").asText("medium").toLowerCase(Locale.ROOT);
        if (!PRIORITIES.contains(priority)) {
            priority = "medium";
        }
        String category = node.path("category").asText("other").toLowerCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            category = "other";
        }
        String repeat = firstNonBlank(
                node.path("repeat_pattern").asText(null),
                node.path("repeat").asText(null),
                "none");
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("title", title);
        draft.put("due", due.replace(' ', 'T'));
        draft.put("priority", priority);
        draft.put("category", category);
        draft.put("repeat_pattern", repeat == null ? "none" : repeat);
        draft.put(
                "notification_type",
                firstNonBlank(node.path("notification_type").asText(null), "push"));
        return draft;
    }

    private static Object jsonValue(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.numberValue();
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isArray()) {
            List<Object> list = new ArrayList<>();
            n.forEach(x -> list.add(jsonValue(x)));
            return list;
        }
        return n.asText();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
