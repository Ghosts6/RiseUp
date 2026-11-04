package com.riseup.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MockProvider implements LLMProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String mode;
    public final List<String> calls = new ArrayList<>();

    public MockProvider(String mode) {
        this.mode = mode == null ? "normal" : mode;
    }

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public String getModel() {
        return "riseup-mock-1";
    }

    @Override
    public String complete(String prompt, String system) {
        calls.add(prompt);
        if ("error".equals(mode)) throw new LLMProviderError("Simulated LLM outage");
        if ("invalid_json".equals(mode)) return "Sure! I think a phone call would be best here.";
        String task = readTask(prompt);
        Map<String, Object> ctx = readContext(prompt);
        try {
            return MAPPER.writeValueAsString(handle(task, ctx));
        } catch (Exception e) {
            throw new LLMProviderError("mock serialize failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handle(String task, Map<String, Object> ctx) {
        return switch (task) {
            case "escalation" -> escalation(ctx);
            case "parse_reminder" -> MockNlp.parseReminderText(
                    String.valueOf(ctx.getOrDefault("text", "")),
                    String.valueOf(ctx.getOrDefault("now_local", "")));
            case "prioritize" -> prioritize(ctx);
            case "recommendations" -> Map.of("items", List.of());
            default -> Map.of("error", "unknown task " + task);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> escalation(Map<String, Object> ctx) {
        List<String> available = (List<String>) ctx.getOrDefault("available_methods", List.of());
        if ("disabled_method".equals(mode)) {
            List<String> disabled = new ArrayList<>();
            for (String m : List.of("call", "sms", "alexa", "emergency_contact")) {
                if (!available.contains(m)) disabled.add(m);
            }
            Map<String, Object> bad = new LinkedHashMap<>();
            bad.put("method", disabled.isEmpty() ? "carrier_pigeon" : disabled.get(0));
            bad.put("reasoning", "Simulated invalid pick");
            bad.put("confidence", 0.9);
            bad.put("fallback", null);
            return bad;
        }
        Map<String, Object> stats = (Map<String, Object>) ctx.getOrDefault("escalation_stats", Map.of());
        List<String> order = (List<String>) ctx.getOrDefault("escalation_order", available);
        List<String> ranked = new ArrayList<>(available);
        ranked.sort(
                Comparator.comparingDouble(
                                (String method) -> {
                                    Object entry = stats.get(method);
                                    double rate = 0.5;
                                    if (entry instanceof Map<?, ?> m) {
                                        Object attempts = m.get("attempts");
                                        Object sr = m.get("success_rate");
                                        if (attempts instanceof Number a && a.intValue() > 0 && sr instanceof Number r) {
                                            rate = r.doubleValue();
                                        }
                                    }
                                    if ("emergency_contact".equals(method)) rate -= 1.0;
                                    return -rate;
                                })
                        .thenComparingInt(method -> order.contains(method) ? order.indexOf(method) : order.size()));
        String choice = ranked.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", choice);
        Object entry = stats.get(choice);
        if (entry instanceof Map<?, ?> m
                && m.get("attempts") instanceof Number a
                && a.intValue() > 0
                && m.get("success_rate") instanceof Number r) {
            out.put(
                    "reasoning",
                    choice
                            + " has woken you "
                            + String.format(Locale.US, "%.0f%%", r.doubleValue() * 100)
                            + " of the time ("
                            + a.intValue()
                            + " attempts), the best of the enabled methods.");
            out.put("confidence", Math.round(Math.min(0.95, 0.5 + r.doubleValue() / 2) * 100.0) / 100.0);
        } else {
            out.put(
                    "reasoning",
                    "No history for the remaining methods; following your preferred order (" + choice + " first).");
            out.put("confidence", 0.55);
        }
        out.put("fallback", ranked.size() > 1 ? ranked.get(1) : null);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> prioritize(Map<String, Object> ctx) {
        Map<String, Integer> rank = Map.of("critical", 0, "high", 1, "medium", 2, "low", 3);
        List<Map<String, Object>> items = (List<Map<String, Object>>) ctx.getOrDefault("reminders", List.of());
        List<Map<String, Object>> ordered = new ArrayList<>(items);
        ordered.sort(
                Comparator.comparingInt(
                                (Map<String, Object> r) ->
                                        rank.getOrDefault(String.valueOf(r.get("priority")), 2))
                        .thenComparing(r -> String.valueOf(r.getOrDefault("due_at", ""))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order", ordered.stream().map(r -> r.get("id")).toList());
        out.put(
                "reasoning",
                ordered.isEmpty()
                        ? "No reminders"
                        : "'" + ordered.get(0).get("title") + "' goes first: " + ordered.get(0).get("priority") + " priority");
        return out;
    }

    private static String readTask(String prompt) {
        Matcher m = Pattern.compile("TASK_TYPE:\\s*(\\w+)").matcher(prompt);
        return m.find() ? m.group(1) : "unknown";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readContext(String prompt) {
        Matcher m = Pattern.compile("CONTEXT_JSON:\\s*(\\{.*?\\})\\s*\\nTASK:", Pattern.DOTALL).matcher(prompt);
        if (!m.find()) return Map.of();
        try {
            return MAPPER.readValue(m.group(1), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
