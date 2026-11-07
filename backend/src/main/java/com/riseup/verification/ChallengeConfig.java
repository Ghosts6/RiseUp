package com.riseup.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Stored in alarm.custom_task_text as JSON for math/trivia (and optional custom text). */
public record ChallengeConfig(String difficulty, int questions, String text) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ChallengeConfig defaults() {
        return new ChallengeConfig("medium", 1, "");
    }

    public static ChallengeConfig parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaults();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode n = MAPPER.readTree(trimmed);
                String diff = n.path("difficulty").asText("medium").toLowerCase(Locale.ROOT);
                int q = Math.max(1, Math.min(5, n.path("questions").asInt(1)));
                String text = n.path("text").asText("");
                if (!isDifficulty(diff)) {
                    diff = "medium";
                }
                return new ChallengeConfig(diff, q, text);
            } catch (Exception ignored) {
                return new ChallengeConfig("medium", 1, trimmed);
            }
        }
        // Legacy plain custom description
        return new ChallengeConfig("medium", 1, trimmed);
    }

    public String toStorage() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("difficulty", difficulty == null ? "medium" : difficulty);
        m.put("questions", Math.max(1, Math.min(5, questions)));
        if (text != null && !text.isBlank()) {
            m.put("text", text);
        }
        try {
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            return "{\"difficulty\":\"medium\",\"questions\":1}";
        }
    }

    public static boolean isDifficulty(String d) {
        return "easy".equals(d) || "medium".equals(d) || "hard".equals(d);
    }
}
