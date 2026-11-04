package com.riseup.reminder;

import com.riseup.alarm.StatusEmitter;
import com.riseup.clock.Clock;
import com.riseup.config.RiseUpProperties;
import com.riseup.domain.ReminderEntity;
import com.riseup.domain.ReminderLogEntity;
import com.riseup.domain.UserEntity;
import com.riseup.domain.UserProfileEntity;
import com.riseup.error.ConflictException;
import com.riseup.error.NotFoundException;
import com.riseup.error.ValidationFailedException;
import com.riseup.repository.ReminderLogRepository;
import com.riseup.repository.ReminderRepository;
import com.riseup.repository.UserProfileRepository;
import com.riseup.repository.UserRepository;
import com.riseup.time.TimeUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderControllerService {
    private static final Set<String> EDITABLE =
            Set.of("title", "notes", "priority", "category", "repeat_pattern", "notification_type");

    private final ReminderRepository reminders;
    private final ReminderLogRepository reminderLogs;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final ReminderFactory factory;
    private final ReminderSchedulerService scheduler;
    private final Clock clock;
    private final RiseUpProperties settings;
    private final StatusEmitter emitter;
    private final ReminderPrioritizer prioritizer;

    public ReminderControllerService(
            ReminderRepository reminders,
            ReminderLogRepository reminderLogs,
            UserRepository users,
            UserProfileRepository profiles,
            ReminderFactory factory,
            ReminderSchedulerService scheduler,
            Clock clock,
            RiseUpProperties settings,
            StatusEmitter emitter,
            ReminderPrioritizer prioritizer) {
        this.reminders = reminders;
        this.reminderLogs = reminderLogs;
        this.users = users;
        this.profiles = profiles;
        this.factory = factory;
        this.scheduler = scheduler;
        this.clock = clock;
        this.settings = settings;
        this.emitter = emitter;
        this.prioritizer = prioritizer;
    }

    public List<ReminderEntity> list(int userId, String status, String category, String priority) {
        List<ReminderEntity> all = reminders.findByUserIdOrderByDueAtAsc(userId);
        return all.stream()
                .filter(r -> {
                    if (status == null) return true;
                    if ("open".equals(status)) return r.isOpen();
                    return status.equals(r.getStatus());
                })
                .filter(r -> category == null || category.equals(r.getCategory()))
                .filter(r -> priority == null || priority.equals(r.getPriority()))
                .toList();
    }

    public ReminderEntity get(int userId, int reminderId) {
        return reminders
                .findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new NotFoundException("Reminder " + reminderId + " not found"));
    }

    @Transactional
    public ReminderEntity create(int userId, Map<String, Object> data, String source) {
        ReminderEntity reminder =
                factory.create(userId, data, source, Boolean.TRUE.equals(data.get("allow_past")));
        reminders.save(reminder);
        emitter.emit(
                "reminder",
                reminder.getId(),
                userId,
                "scheduled",
                Map.of("event", "created", "source", source, "title", reminder.getTitle()));
        scheduler.scheduleReminder(reminder);
        return reminder;
    }

    @Transactional
    public ReminderEntity update(int userId, int reminderId, Map<String, Object> data, String scope) {
        ReminderEntity reminder = get(userId, reminderId);
        if ("completed".equals(reminder.getStatus())) {
            throw new ConflictException("Completed reminders cannot be edited");
        }
        if (!"this".equals(scope) && !"all".equals(scope)) {
            throw new ValidationFailedException("scope must be 'this' or 'all'");
        }
        if (data.get("title") != null) reminder.setTitle(String.valueOf(data.get("title")));
        if (data.get("notes") != null) reminder.setNotes(String.valueOf(data.get("notes")));
        if (data.get("priority") != null) reminder.setPriority(String.valueOf(data.get("priority")));
        if (data.get("category") != null) reminder.setCategory(String.valueOf(data.get("category")));
        if (data.get("repeat_pattern") != null) reminder.setRepeatPattern(String.valueOf(data.get("repeat_pattern")));
        if (data.get("notification_type") != null)
            reminder.setNotificationType(String.valueOf(data.get("notification_type")));
        UserEntity user = users.findById(userId).orElseThrow();
        boolean reschedule = false;
        Object dueValue = data.get("due") != null ? data.get("due") : data.get("due_at");
        if (dueValue != null) {
            reminder.setDueAt(ReminderFactory.parseDue(dueValue, user.getTimezone()));
            if (reminder.getDueAt().isBefore(clock.now())) {
                throw new ValidationFailedException("This time has passed. Set for tomorrow instead?", "due");
            }
            reschedule = true;
        }
        if (data.containsKey("extra_times") && data.get("extra_times") instanceof List<?> list) {
            List<Object> extras = new ArrayList<>();
            for (Object t : list) extras.add(ReminderFactory.parseDue(t, user.getTimezone()).toString());
            reminder.setExtraTimes(extras);
            reschedule = true;
        }
        if (!"none".equals(reminder.getRepeatPattern())) {
            if (reminder.getSeriesId() == null) reminder.setSeriesId("s" + reminder.getId());
            if ("all".equals(scope) || reminder.getSeriesTime() == null) {
                var local = TimeUtil.toLocal(reminder.getDueAt(), user.getTimezone());
                reminder.setSeriesTime(String.format("%02d:%02d", local.getHour(), local.getMinute()));
            }
        }
        reminder.setUpdatedAt(clock.now());
        if (reschedule) {
            scheduler.cancel(reminder);
            reminder.setEscalationLevel(0);
            reminder.setFirstNotifiedAt(null);
            reminder.setStatus("scheduled");
            reminders.save(reminder);
            scheduler.scheduleReminder(reminder);
        } else {
            reminders.save(reminder);
        }
        emitter.emit("reminder", reminder.getId(), userId, reminder.getStatus(), Map.of("event", "updated", "scope", scope));
        return reminder;
    }

    @Transactional
    public ReminderEntity complete(int userId, int reminderId, boolean alreadyDone) {
        ReminderEntity reminder = get(userId, reminderId);
        if ("completed".equals(reminder.getStatus())) {
            throw new ConflictException("Reminder is already completed");
        }
        scheduler.cancel(reminder);
        reminder.setStatus("completed");
        reminder.setCompletedAt(clock.now());
        reminder.setUpdatedAt(clock.now());
        reminders.save(reminder);
        emitter.emit(
                "reminder",
                reminder.getId(),
                userId,
                "completed",
                Map.of("event", "completed", "title", reminder.getTitle(), "already_done", alreadyDone));
        spawnNext(reminder);
        return reminder;
    }

    @Transactional
    public ReminderEntity snooze(int userId, int reminderId, Integer minutes) {
        ReminderEntity reminder = get(userId, reminderId);
        if (!reminder.isOpen()) {
            throw new ConflictException("Reminder is already " + reminder.getStatus() + "; it cannot be snoozed");
        }
        int mins = minutes == null ? settings.getReminderSnoozeMinutes() : minutes;
        if (mins < 1 || mins > 24 * 60) {
            throw new ValidationFailedException("Snooze must be between 1 minute and 24 hours");
        }
        scheduler.cancel(reminder);
        reminder.setDueAt(clock.now().plusMinutes(mins));
        reminder.setEscalationLevel(0);
        reminder.setFirstNotifiedAt(null);
        reminder.setSnoozeCount(reminder.getSnoozeCount() + 1);
        reminder.setStatus("scheduled");
        reminder.setUpdatedAt(clock.now());
        reminders.save(reminder);
        emitter.emit(
                "reminder",
                reminder.getId(),
                userId,
                "scheduled",
                Map.of("event", "snoozed", "minutes", mins, "title", reminder.getTitle()));
        scheduler.scheduleReminder(reminder);
        return reminder;
    }

    @Transactional
    public void delete(int userId, int reminderId) {
        ReminderEntity reminder = get(userId, reminderId);
        scheduler.cancel(reminder);
        ReminderLogEntity log = new ReminderLogEntity();
        log.setUserId(userId);
        log.setReminderId(reminderId);
        log.setEvent("deleted");
        log.setDetails(Map.of("title", reminder.getTitle()));
        log.setCreatedAt(clock.now());
        reminderLogs.save(log);
        reminders.delete(reminder);
    }

    @Transactional
    public ReminderEntity spawnNext(ReminderEntity reminder) {
        UserEntity user = users.findById(reminder.getUserId()).orElseThrow();
        LocalDateTime due =
                TimeUtil.nextReminderOccurrence(
                        reminder.getDueAt(), reminder.getRepeatPattern(), reminder.getSeriesTime(), user.getTimezone());
        if (due == null) return null;
        LocalDateTime now = clock.now();
        while (due != null && !due.isAfter(now)) {
            due =
                    TimeUtil.nextReminderOccurrence(
                            due, reminder.getRepeatPattern(), reminder.getSeriesTime(), user.getTimezone());
        }
        if (due == null) return null;
        ReminderEntity nxt = factory.nextOccurrence(reminder, due);
        reminders.save(nxt);
        emitter.emit(
                "reminder",
                nxt.getId(),
                nxt.getUserId(),
                "scheduled",
                Map.of("event", "created", "source", "recurrence", "title", nxt.getTitle()));
        scheduler.scheduleReminder(nxt);
        return nxt;
    }

    public Map<String, Object> priorityPreview(int userId) {
        LocalDateTime window = clock.now().plusHours(1);
        List<ReminderEntity> due =
                list(userId, "open", null, null).stream().filter(r -> !r.getDueAt().isAfter(window)).toList();
        UserProfileEntity profile =
                profiles.findById(userId).orElseGet(() -> {
                    UserProfileEntity p = new UserProfileEntity();
                    p.setUserId(userId);
                    p.setUpdatedAt(clock.now());
                    return profiles.save(p);
                });
        UserEntity user = users.findById(userId).orElseThrow();
        var result = prioritizer.prioritize(due, profile, clock.now(), user.getTimezone());
        Map<Integer, ReminderEntity> byId = new LinkedHashMap<>();
        for (ReminderEntity r : due) byId.put(r.getId(), r);
        List<ReminderEntity> order = new ArrayList<>();
        for (Integer id : result.order()) {
            if (byId.containsKey(id)) order.add(byId.get(id));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order", order);
        out.put("reasoning", result.reasoning());
        out.put("decided_by", result.decidedBy());
        out.put("fallback_used", result.fallbackUsed());
        return out;
    }
}
