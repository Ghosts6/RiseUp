package com.riseup.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class PromptBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public final String system =
            "You are RiseUp's decision agent. Reply with JSON only matching the task schema.";

    public String buildEscalationPrompt(Map<String, Object> inputs) {
        return build("escalation", inputs, "Choose the best escalation method from available_methods.");
    }

    public String buildParsePrompt(Map<String, Object> inputs) {
        return build("parse_reminder", inputs, "Extract a reminder draft from text.");
    }

    public String buildPrioritizePrompt(Map<String, Object> inputs) {
        return build("prioritize", inputs, "Order reminders by urgency.");
    }

    public String buildRecommendPrompt(Map<String, Object> inputs) {
        return build("recommendations", inputs, "Suggest preference changes.");
    }

    private String build(String task, Map<String, Object> inputs, String instruction) {
        try {
            return "TASK_TYPE: "
                    + task
                    + "\nCONTEXT_JSON: "
                    + MAPPER.writeValueAsString(inputs)
                    + "\nTASK: "
                    + instruction;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
