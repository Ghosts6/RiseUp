package com.riseup.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class PromptBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public final String system =
            "You are RiseUp's decision agent. Reply with JSON only matching the task schema.";

    public String chatSystem(String knowledgeBase) {
        return """
                You are RiseUp Agent — the in-app AI assistant for the RiseUp wake-up and
                reminder product. Help users understand the app, choose settings, and get
                practical feedback on morning routines, alarms, challenges, and tasks.

                Tone: warm, clear, concise. Prefer short paragraphs or bullets.
                Stay within RiseUp's product scope.

                You may propose constrained tools by ending with a JSON object on its own line:
                {"tool":"create_reminder","args":{"title":"...","due":"YYYY-MM-DDTHH:MM","priority":"medium","category":"other"}}
                {"tool":"create_alarm","args":{"time":"07:00","label":"...","repeat_pattern":"once"}}
                {"tool":"list_today","args":{}}
                Never claim a write succeeded until the client confirms. For ordinary Q&A, reply in plain text only.

                Use this knowledge base when answering product questions:
                ---
                """
                + (knowledgeBase == null ? "" : knowledgeBase)
                + "\n---";
    }

    public String buildEscalationPrompt(Map<String, Object> inputs) {
        return build("escalation", inputs, "Choose the best escalation method from available_methods.");
    }

    public String buildParsePrompt(Map<String, Object> inputs) {
        return build(
                "parse_reminder",
                inputs,
                """
                Extract a reminder draft from text.
                OUTPUT FORMAT (JSON only):
                {"status":"ok|needs_clarification|past_time","question":string|null,
                 "draft":{"title":string,"due":"YYYY-MM-DDTHH:MM:SS","priority":"low|medium|high|critical",
                          "category":"work|personal|health|other","repeat_pattern":"none|daily|weekly|weekdays",
                          "notification_type":"push"}|null}
                due must be local wall time in the user's timezone from CONTEXT_JSON.
                """);
    }

    public String buildPrioritizePrompt(Map<String, Object> inputs) {
        return build(
                "prioritize",
                inputs,
                """
                Order reminders by urgency for this user.
                OUTPUT FORMAT (JSON only):
                {"order":[id,...],"reasoning":"short explanation"}
                order must list every reminder id exactly once.
                """);
    }

    public String buildRecommendPrompt(Map<String, Object> inputs) {
        return build(
                "recommendations",
                inputs,
                """
                Suggest up to 5 safe preference changes.
                OUTPUT FORMAT (JSON only):
                {"items":[{"type":string,"title":string,"rationale":string,
                  "change":{"checkin_delay_minutes":number}|{"add_channel":"sms|call|push|alexa"}|{} }]}
                Do not disable all escalation channels.
                """);
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
