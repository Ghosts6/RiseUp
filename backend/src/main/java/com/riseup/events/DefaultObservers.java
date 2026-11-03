package com.riseup.events;

import com.riseup.clock.Clock;
import com.riseup.domain.NotificationEntity;
import com.riseup.domain.ReminderEntity;
import com.riseup.domain.ReminderLogEntity;
import com.riseup.repository.NotificationRepository;
import com.riseup.repository.ReminderLogRepository;
import com.riseup.repository.ReminderRepository;
import com.riseup.realtime.WebSocketHub;
import com.riseup.time.TimeUtil;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DefaultObservers {
    private final EventBus bus;
    private final NotificationRepository notifications;
    private final ReminderLogRepository reminderLogs;
    private final ReminderRepository reminders;
    private final WebSocketHub hub;
    private final Clock clock;

    public DefaultObservers(
            EventBus bus,
            NotificationRepository notifications,
            ReminderLogRepository reminderLogs,
            ReminderRepository reminders,
            WebSocketHub hub,
            Clock clock) {
        this.bus = bus;
        this.notifications = notifications;
        this.reminderLogs = reminderLogs;
        this.reminders = reminders;
        this.hub = hub;
        this.clock = clock;
    }

    @PostConstruct
    public void register() {
        bus.register(this::dashboard);
        bus.register(
                new StatusObserver() {
                    @Override
                    public boolean caresAbout(String subjectType) {
                        return "reminder".equals(subjectType);
                    }

                    @Override
                    public void onStatusChanged(StatusEvent event) {
                        reminderLogger(event);
                    }
                });
    }

    private String[] describe(StatusEvent event) {
        Map<String, Object> p = event.payload();
        String label = String.valueOf(p.getOrDefault("label", "Alarm"));
        if ("alarm".equals(event.subjectType())) {
            return switch (event.status()) {
                case "ringing" -> new String[] {label + " is ringing", "Complete the challenge to dismiss it."};
                case "escalating" -> new String[] {label + ": escalation started", "No response yet, RiseUp's agent is escalating."};
                case "missed" -> new String[] {label + " went unanswered", "Alarm at " + p.getOrDefault("time", "") + " was missed."};
                case "completed" -> new String[] {label + " completed", "Nice work, you're up!"};
                default -> null;
            };
        }
        if ("checkin".equals(event.subjectType())) {
            return switch (event.status()) {
                case "sent" -> new String[] {"Are you still awake?", "Confirm your check-in."};
                case "missed" -> new String[] {"Check-in missed", "Escalation will begin."};
                default -> null;
            };
        }
        if ("reminder".equals(event.subjectType())) {
            String title = String.valueOf(p.getOrDefault("title", "Reminder"));
            String ev = String.valueOf(p.getOrDefault("event", ""));
            return switch (ev) {
                case "primary_sent" -> new String[] {"Reminder: " + title, "It's time."};
                case "escalation_sent" -> new String[] {"Still to do: " + title, "Second nudge."};
                case "final_sent" -> new String[] {"Last reminder: " + title, "Final nudge before it's marked missed."};
                case "missed" -> new String[] {"Missed: " + title, "Marked as missed."};
                case "queued" -> new String[] {"Reminder: " + title, "Delivery failed; queued for retry and shown here."};
                case "retry_sent" -> new String[] {"Reminder retry: " + title, "Queued notification was delivered."};
                default -> null;
            };
        }
        if ("escalation".equals(event.subjectType())) {
            String method = String.valueOf(p.getOrDefault("method", "?"));
            return switch (event.status()) {
                case "sent" -> new String[] {"Escalation: " + method + " sent", String.valueOf(p.getOrDefault("reasoning", "")).substring(0, Math.min(180, String.valueOf(p.getOrDefault("reasoning", "")).length()))};
                case "failed" -> new String[] {"Escalation: " + method + " failed", String.valueOf(p.getOrDefault("error", ""))};
                case "confirmed" -> new String[] {"Escalation confirmed", "You responded via " + method + "."};
                default -> null;
            };
        }
        return null;
    }

    @Transactional
    protected void dashboard(StatusEvent event) {
        String[] message = describe(event);
        Integer notificationId = null;
        if (message != null) {
            NotificationEntity note = new NotificationEntity();
            note.setUserId(event.userId());
            note.setKind(event.subjectType());
            note.setTitle(message[0]);
            note.setBody(message[1]);
            note.setData(Map.of("subject_id", event.subjectId(), "status", event.status()));
            note.setRead(false);
            note.setCreatedAt(clock.now());
            notifications.save(note);
            notificationId = note.getId();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "event");
        payload.put("subject_type", event.subjectType());
        payload.put("subject_id", event.subjectId());
        payload.put("status", event.status());
        payload.put("event", event.payload().get("event"));
        payload.put("title", message == null ? null : message[0]);
        payload.put("body", message == null ? null : message[1]);
        payload.put("notification_id", notificationId);
        payload.put("at", TimeUtil.isoZ(event.at()));
        hub.broadcast(event.userId(), payload);
    }

    @Transactional
    protected void reminderLogger(StatusEvent event) {
        Map<String, Object> payload = new HashMap<>(event.payload());
        ReminderEntity reminder = reminders.findById(event.subjectId()).orElse(null);
        ReminderLogEntity log = new ReminderLogEntity();
        log.setUserId(event.userId());
        log.setReminderId(event.subjectId());
        log.setCategory(reminder == null ? null : reminder.getCategory());
        Object ev = payload.remove("event");
        log.setEvent(ev == null ? event.status() : String.valueOf(ev));
        Object level = payload.remove("level");
        if (level instanceof Number n) log.setLevel(n.intValue());
        Object channel = payload.remove("channel");
        if (channel != null) log.setChannel(String.valueOf(channel));
        Map<String, Object> details = new HashMap<>(payload);
        details.put("title", reminder == null ? null : reminder.getTitle());
        details.put("priority", reminder == null ? null : reminder.getPriority());
        log.setDetails(details);
        log.setCreatedAt(clock.now());
        reminderLogs.save(log);
    }
}
