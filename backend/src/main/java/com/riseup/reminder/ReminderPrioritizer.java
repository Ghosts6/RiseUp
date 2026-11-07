package com.riseup.reminder;

import com.riseup.ai.LLMClient;
import com.riseup.ai.LLMProviderError;
import com.riseup.ai.LLMResponse;
import com.riseup.ai.PromptBuilder;
import com.riseup.ai.ResponseParser;
import com.riseup.domain.ReminderEntity;
import com.riseup.domain.UserProfileEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReminderPrioritizer {
    public record Result(List<Integer> order, String reasoning, String decidedBy, boolean fallbackUsed) {}

    private final LLMClient llm;
    private final PromptBuilder prompts = new PromptBuilder();
    private final ResponseParser parser = new ResponseParser();

    public ReminderPrioritizer(LLMClient llm) {
        this.llm = llm;
    }

    public Result prioritize(
            List<ReminderEntity> due, UserProfileEntity profile, LocalDateTime now, String tz) {
        Result rules = rulesPrioritize(due);
        if (due == null || due.size() <= 1) {
            return rules;
        }
        if ("mock".equals(llm.providerFor("prioritize").getName())) {
            // Mock still exercises the LLM path so tests cover the wire format
            try {
                return llmPrioritize(due, profile, now, tz, rules);
            } catch (Exception e) {
                return rules;
            }
        }
        try {
            return llmPrioritize(due, profile, now, tz, rules);
        } catch (Exception e) {
            return new Result(rules.order(), rules.reasoning() + " (LLM fallback)", "rules", true);
        }
    }

    private Result llmPrioritize(
            List<ReminderEntity> due,
            UserProfileEntity profile,
            LocalDateTime now,
            String tz,
            Result rulesFallback) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ReminderEntity r : due) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("title", r.getTitle());
            row.put("priority", r.getPriority());
            row.put("category", r.getCategory());
            row.put("due_at", r.getDueAt() == null ? null : r.getDueAt().toString());
            items.add(row);
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("reminders", items);
        inputs.put("now", now.toString());
        inputs.put("timezone", tz);
        inputs.put(
                "task_completion_rates",
                profile == null ? Map.of() : profile.getTaskCompletionRates());

        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                LLMResponse resp =
                        llm.sendRequest(prompts.buildPrioritizePrompt(inputs), "prioritize", prompts.system);
                Map<String, Object> parsed =
                        parser.parsePrioritize(
                                resp.text(), due.stream().map(ReminderEntity::getId).toList());
                @SuppressWarnings("unchecked")
                List<Integer> order = (List<Integer>) parsed.get("order");
                return new Result(
                        order,
                        String.valueOf(parsed.getOrDefault("reasoning", "")),
                        resp.decidedBy(),
                        false);
            } catch (ResponseParser.ParseError | LLMProviderError e) {
                last = e;
            }
        }
        return new Result(
                rulesFallback.order(),
                rulesFallback.reasoning()
                        + (last == null ? "" : " (LLM unavailable: " + last.getMessage() + ")"),
                "rules",
                true);
    }

    private Result rulesPrioritize(List<ReminderEntity> due) {
        Map<String, Integer> rank = Map.of("critical", 0, "high", 1, "medium", 2, "low", 3);
        List<ReminderEntity> ordered = new ArrayList<>(due == null ? List.of() : due);
        ordered.sort(
                Comparator.comparingInt((ReminderEntity r) -> rank.getOrDefault(r.getPriority(), 2))
                        .thenComparing(ReminderEntity::getDueAt));
        String reasoning =
                ordered.isEmpty()
                        ? "No reminders due"
                        : "'"
                                + ordered.get(0).getTitle()
                                + "' goes first: "
                                + ordered.get(0).getPriority()
                                + " priority; the rest follow by priority then due time.";
        return new Result(ordered.stream().map(ReminderEntity::getId).toList(), reasoning, "rules", false);
    }
}
