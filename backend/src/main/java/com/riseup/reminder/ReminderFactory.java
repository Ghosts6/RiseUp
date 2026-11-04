package com.riseup.reminder;

import com.riseup.clock.Clock;
import com.riseup.domain.ReminderEntity;
import com.riseup.domain.UserEntity;
import com.riseup.error.ValidationFailedException;
import com.riseup.repository.UserRepository;
import com.riseup.time.TimeUtil;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReminderFactory {
    private final UserRepository users;
    private final Clock clock;

    public ReminderFactory(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public static LocalDateTime parseDue(Object value, String tzName) {
        if (value instanceof LocalDateTime ldt) return TimeUtil.toUtcNaive(ldt, tzName);
        if (value instanceof String s && !s.isBlank()) {
            try {
                String raw = s.strip().replace("Z", "+00:00");
                if (raw.contains("+") || raw.endsWith("Z")) {
                    return OffsetDateTime.parse(raw.replace(" ", "T")).toLocalDateTime();
                }
                LocalDateTime local = LocalDateTime.parse(raw.replace(" ", "T"));
                return TimeUtil.toUtcNaive(local, tzName);
            } catch (DateTimeParseException e) {
                throw new ValidationFailedException("Invalid date/time. Use YYYY-MM-DD HH:MM", "due");
            }
        }
        throw new ValidationFailedException("Due date/time is required", "due");
    }

    public ReminderEntity create(int userId, Map<String, Object> fields, String source, boolean allowPast) {
        UserEntity user = users.findById(userId).orElseThrow();
        LocalDateTime dueAt = parseDue(fields.get("due") != null ? fields.get("due") : fields.get("due_at"), user.getTimezone());
        LocalDateTime now = clock.now();
        if (dueAt.isBefore(now.minusMinutes(1)) && !allowPast) {
            LocalDateTime suggestion = dueAt;
            while (suggestion.isBefore(now)) suggestion = suggestion.plusDays(1);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("field", "due");
            extra.put(
                    "suggestion",
                    TimeUtil.toLocal(suggestion, user.getTimezone()).toString().substring(0, 16));
            throw new ValidationFailedException("This time has passed. Set for tomorrow instead?", extra);
        }
        ReminderEntity r = new ReminderEntity();
        r.setUserId(userId);
        r.setTitle(fields.get("title") == null ? "" : String.valueOf(fields.get("title")).strip());
        r.setNotes(fields.get("notes") == null ? null : String.valueOf(fields.get("notes")));
        r.setDueAt(dueAt);
        r.setPriority(str(fields.get("priority"), "medium"));
        r.setCategory(str(fields.get("category"), "other"));
        r.setRepeatPattern(str(fields.get("repeat_pattern"), "none"));
        r.setNotificationType(str(fields.get("notification_type"), "push"));
        r.setSource(source);
        r.setStatus("scheduled");
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        List<Object> extras = new ArrayList<>();
        Object et = fields.get("extra_times");
        if (et instanceof List<?> list) {
            for (Object t : list) extras.add(parseDue(t, user.getTimezone()).toString());
        }
        r.setExtraTimes(extras);
        if (!"none".equals(r.getRepeatPattern())) {
            r.setSeriesId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            r.setSeriesTime(String.format("%02d:%02d", TimeUtil.toLocal(dueAt, user.getTimezone()).getHour(), TimeUtil.toLocal(dueAt, user.getTimezone()).getMinute()));
        }
        if (r.getTitle() == null || r.getTitle().isBlank()) {
            throw new ValidationFailedException("Title is required", "title");
        }
        return r;
    }

    public ReminderEntity nextOccurrence(ReminderEntity reminder, LocalDateTime dueAt) {
        ReminderEntity nxt = new ReminderEntity();
        nxt.setUserId(reminder.getUserId());
        nxt.setTitle(reminder.getTitle());
        nxt.setNotes(reminder.getNotes());
        nxt.setDueAt(dueAt);
        nxt.setPriority(reminder.getPriority());
        nxt.setCategory(reminder.getCategory());
        nxt.setRepeatPattern(reminder.getRepeatPattern());
        nxt.setSeriesId(reminder.getSeriesId());
        nxt.setSeriesTime(reminder.getSeriesTime());
        nxt.setNotificationType(reminder.getNotificationType());
        nxt.setSource(reminder.getSource());
        nxt.setStatus("scheduled");
        nxt.setCreatedAt(clock.now());
        nxt.setUpdatedAt(clock.now());
        nxt.setExtraTimes(new ArrayList<>());
        return nxt;
    }

    private static String str(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }
}
