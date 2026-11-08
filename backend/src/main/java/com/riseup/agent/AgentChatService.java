package com.riseup.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseup.ai.AppKnowledge;
import com.riseup.ai.LLMClient;
import com.riseup.ai.LLMResponse;
import com.riseup.ai.PromptBuilder;
import com.riseup.ai.ResponseParser;
import com.riseup.clock.Clock;
import com.riseup.repository.UserRepository;
import com.riseup.time.TimeUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AgentChatService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern REMIND_INTENT =
            Pattern.compile("\\b(remind me|set a reminder|create a reminder|add a reminder)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TODAY_INTENT =
            Pattern.compile("\\b(what('?s| is) on today|morning briefing|today'?s (plan|schedule)|list today)\\b",
                    Pattern.CASE_INSENSITIVE);

    private final LLMClient llm;
    private final AgentToolService tools;
    private final BriefingService briefing;
    private final UserRepository users;
    private final Clock clock;
    private final PromptBuilder prompts = new PromptBuilder();
    private final ResponseParser parser = new ResponseParser();

    public AgentChatService(
            LLMClient llm, AgentToolService tools, BriefingService briefing, UserRepository users, Clock clock) {
        this.llm = llm;
        this.tools = tools;
        this.briefing = briefing;
        this.users = users;
        this.clock = clock;
    }

    public Map<String, Object> chat(int userId, String message, List<Map<String, String>> history) {
        String userMessage = message == null ? "" : message.strip();
        if (userMessage.isEmpty()) {
            throw new IllegalArgumentException("Message is required");
        }
        if (userMessage.length() > 4000) {
            userMessage = userMessage.substring(0, 4000);
        }

        if (TODAY_INTENT.matcher(userMessage).find()) {
            Map<String, Object> today = briefing.today(userId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("reply", String.valueOf(today.get("summary")));
            out.put("briefing", today);
            out.put("provider", "rules");
            out.put("model", "briefing");
            return out;
        }

        if (REMIND_INTENT.matcher(userMessage).find()) {
            Map<String, Object> proposal = tools.proposeReminderFromText(userId, userMessage);
            Map<String, Object> out = new LinkedHashMap<>();
            Object parse = proposal.get("parse");
            String status = parse instanceof Map<?, ?> m ? String.valueOf(m.get("status")) : "";
            if ("ok".equals(status) && proposal.get("args") instanceof Map<?, ?> args && !args.isEmpty()) {
                out.put("reply", String.valueOf(proposal.get("confirm_message")));
                out.put("proposed_action", Map.of(
                        "tool", "create_reminder",
                        "args", proposal.get("args"),
                        "confirm_message", proposal.get("confirm_message")));
            } else {
                out.put("reply", String.valueOf(proposal.get("confirm_message")));
            }
            out.put("provider", "hybrid");
            out.put("model", "nl-parse");
            return out;
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> turn : history) {
                if (turn == null) {
                    continue;
                }
                String role = String.valueOf(turn.getOrDefault("role", "")).strip().toLowerCase(Locale.ROOT);
                String content = String.valueOf(turn.getOrDefault("content", "")).strip();
                if (content.isEmpty()) {
                    continue;
                }
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }
                if (content.length() > 2000) {
                    content = content.substring(0, 2000);
                }
                messages.add(Map.of("role", role, "content", content));
                if (messages.size() >= 16) {
                    break;
                }
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        LLMResponse response = llm.sendChat(messages, prompts.chatSystem(AppKnowledge.TEXT, localNow(userId)));
        Map<String, Object> out = new LinkedHashMap<>();
        String reply = response.text() == null ? "" : response.text().strip();
        Map<String, Object> proposed = extractToolProposal(reply);
        if (proposed != null) {
            out.put("reply", stripToolJson(reply));
            out.put("proposed_action", proposed);
        } else {
            out.put("reply", reply);
        }
        out.put("provider", response.decidedBy());
        out.put("model", response.model());
        return out;
    }

    private String localNow(int userId) {
        String tz = users.findById(userId).map(u -> u.getTimezone()).orElse("UTC");
        LocalDateTime local = TimeUtil.toLocal(clock.now(), tz);
        return local.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.ENGLISH)) + " (" + tz + ")";
    }

    public Map<String, Object> executeTool(int userId, String tool, Map<String, Object> args) {
        return tools.execute(userId, tool, args);
    }

    public Map<String, Object> briefing(int userId) {
        return briefing.today(userId);
    }

    private Map<String, Object> extractToolProposal(String reply) {
        try {
            String json = parser.extractJson(reply);
            JsonNode node = MAPPER.readTree(json);
            String tool = node.path("tool").asText(null);
            if (!tools.isKnownTool(tool)) {
                return null;
            }
            Map<String, Object> args = new LinkedHashMap<>();
            JsonNode argsNode = node.path("args");
            if (argsNode.isObject()) {
                argsNode.fields().forEachRemaining(e -> {
                    if (e.getValue().isNumber()) {
                        args.put(e.getKey(), e.getValue().numberValue());
                    } else if (e.getValue().isBoolean()) {
                        args.put(e.getKey(), e.getValue().asBoolean());
                    } else if (e.getValue().isNull()) {
                        args.put(e.getKey(), null);
                    } else {
                        args.put(e.getKey(), e.getValue().asText());
                    }
                });
            }
            Map<String, Object> proposed = new LinkedHashMap<>();
            proposed.put("tool", tool);
            proposed.put("args", args);
            proposed.put(
                    "confirm_message",
                    "create_reminder".equals(tool)
                            ? "Create this reminder?"
                            : "create_alarm".equals(tool) ? "Create this alarm?" : "Show today's plan?");
            return proposed;
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripToolJson(String reply) {
        Matcher m = Pattern.compile("\\{[\\s\\S]*\"tool\"\\s*:\\s*\"[^\"]+\"[\\s\\S]*\\}\\s*$").matcher(reply);
        if (m.find()) {
            String cleaned = reply.substring(0, m.start()).strip();
            return cleaned.isBlank() ? "I can do that — confirm below." : cleaned;
        }
        return reply;
    }
}
