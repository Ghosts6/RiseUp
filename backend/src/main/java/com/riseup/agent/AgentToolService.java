package com.riseup.agent;

import com.riseup.alarm.AlarmControllerService;
import com.riseup.api.ApiMapper;
import com.riseup.domain.AlarmEntity;
import com.riseup.domain.ReminderEntity;
import com.riseup.domain.UserEntity;
import com.riseup.repository.UserRepository;
import com.riseup.reminder.NlParser;
import com.riseup.reminder.ReminderControllerService;
import com.riseup.clock.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AgentToolService {
    private static final Set<String> TOOLS = Set.of("create_reminder", "create_alarm", "list_today");

    private final ReminderControllerService reminders;
    private final AlarmControllerService alarms;
    private final BriefingService briefing;
    private final NlParser nlParser;
    private final UserRepository users;
    private final Clock clock;

    public AgentToolService(
            ReminderControllerService reminders,
            AlarmControllerService alarms,
            BriefingService briefing,
            NlParser nlParser,
            UserRepository users,
            Clock clock) {
        this.reminders = reminders;
        this.alarms = alarms;
        this.briefing = briefing;
        this.nlParser = nlParser;
        this.users = users;
        this.clock = clock;
    }

    public boolean isKnownTool(String name) {
        return name != null && TOOLS.contains(name);
    }

    public Map<String, Object> execute(int userId, String tool, Map<String, Object> args) {
        String name = tool == null ? "" : tool.strip().toLowerCase(Locale.ROOT);
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        return switch (name) {
            case "create_reminder" -> createReminder(userId, safeArgs);
            case "create_alarm" -> createAlarm(userId, safeArgs);
            case "list_today" -> briefing.today(userId);
            default -> throw new IllegalArgumentException("Unknown tool: " + tool);
        };
    }

    public Map<String, Object> proposeReminderFromText(int userId, String text) {
        UserEntity user = users.findById(userId).orElseThrow();
        Map<String, Object> parsed = nlParser.parse(text, user, clock.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tool", "create_reminder");
        out.put("parse", parsed);
        if ("ok".equals(String.valueOf(parsed.get("status"))) && parsed.get("draft") instanceof Map<?, ?> draft) {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = new LinkedHashMap<>((Map<String, Object>) draft);
            out.put("args", args);
            out.put(
                    "confirm_message",
                    "Create reminder \""
                            + args.get("title")
                            + "\" at "
                            + args.get("due")
                            + "?");
        } else {
            out.put("args", Map.of());
            out.put("confirm_message", String.valueOf(parsed.getOrDefault("question", "Need more details.")));
        }
        return out;
    }

    private Map<String, Object> createReminder(int userId, Map<String, Object> args) {
        ReminderEntity created = reminders.create(userId, args, "agent");
        UserEntity user = users.findById(userId).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("tool", "create_reminder");
        out.put("reminder", ApiMapper.reminder(created, user.getTimezone()));
        out.put("message", "Reminder #" + created.getId() + " scheduled.");
        return out;
    }

    private Map<String, Object> createAlarm(int userId, Map<String, Object> args) {
        Map<String, Object> data = new LinkedHashMap<>(args);
        data.putIfAbsent("label", "Alarm");
        data.putIfAbsent("repeat_pattern", "once");
        data.putIfAbsent("snooze_enabled", false);
        data.putIfAbsent("verification_task_type", "math");
        data.putIfAbsent("is_active", true);
        if (!data.containsKey("time")) {
            throw new IllegalArgumentException("time is required (HH:MM)");
        }
        AlarmEntity created = alarms.createAlarm(userId, data);
        UserEntity user = users.findById(userId).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("tool", "create_alarm");
        out.put("alarm", ApiMapper.alarm(created, user.getTimezone()));
        out.put("message", "Alarm set for " + created.getTime() + ".");
        return out;
    }
}
