package com.riseup.reminder;

import com.riseup.alarm.StatusEmitter;
import com.riseup.clock.Clock;
import com.riseup.config.RiseUpProperties;
import com.riseup.domain.ReminderEntity;
import com.riseup.domain.UserEntity;
import com.riseup.domain.UserPreferencesEntity;
import com.riseup.repository.ReminderRepository;
import com.riseup.repository.UserPreferencesRepository;
import com.riseup.repository.UserRepository;
import com.riseup.scheduling.JobScheduler;
import com.riseup.tools.ToolManager;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderSchedulerService {
    private final ReminderRepository reminders;
    private final UserRepository users;
    private final UserPreferencesRepository prefsRepo;
    private final JobScheduler scheduler;
    private final Clock clock;
    private final RiseUpProperties settings;
    private final ToolManager tools;
    private final StatusEmitter emitter;
    private final ReminderControllerService controller;

    public ReminderSchedulerService(
            ReminderRepository reminders,
            UserRepository users,
            UserPreferencesRepository prefsRepo,
            JobScheduler scheduler,
            Clock clock,
            RiseUpProperties settings,
            ToolManager tools,
            StatusEmitter emitter,
            @org.springframework.context.annotation.Lazy ReminderControllerService controller) {
        this.reminders = reminders;
        this.users = users;
        this.prefsRepo = prefsRepo;
        this.scheduler = scheduler;
        this.clock = clock;
        this.settings = settings;
        this.tools = tools;
        this.emitter = emitter;
        this.controller = controller;
    }

    public static String dueJobId(int id) { return "reminder-due:" + id; }
    public static String missJobId(int id) { return "reminder-miss:" + id; }

    public void scheduleReminder(ReminderEntity reminder) {
        scheduler.schedule(
                dueJobId(reminder.getId()),
                reminder.getDueAt(),
                kwargs -> notifyDue(((Number) kwargs.get("reminder_id")).intValue()),
                Map.of("reminder_id", reminder.getId()));
    }

    public void cancel(ReminderEntity reminder) {
        scheduler.cancel(dueJobId(reminder.getId()));
        scheduler.cancel(missJobId(reminder.getId()));
    }

    @Transactional
    public void notifyDue(int reminderId) {
        ReminderEntity reminder = reminders.findById(reminderId).orElse(null);
        if (reminder == null || !reminder.isOpen()) return;
        UserEntity user = users.findById(reminder.getUserId()).orElseThrow();
        UserPreferencesEntity prefs = prefsRepo.findById(user.getId()).orElseThrow();
        var result =
                tools.sendReminder(
                        user,
                        prefs,
                        reminder.getNotificationType(),
                        "Reminder: " + reminder.getTitle(),
                        reminder.getNotes() == null ? "It's time." : reminder.getNotes());
        if (reminder.getFirstNotifiedAt() == null) reminder.setFirstNotifiedAt(clock.now());
        reminder.setEscalationLevel(reminder.getEscalationLevel() + 1);
        reminder.setStatus(result.isSuccess() ? "notifying" : "queued");
        reminder.setUpdatedAt(clock.now());
        reminders.save(reminder);
        String event =
                reminder.getEscalationLevel() == 1
                        ? "primary_sent"
                        : reminder.getEscalationLevel() == 2 ? "escalation_sent" : "final_sent";
        if (!result.isSuccess()) event = "queued";
        emitter.emit(
                "reminder",
                reminder.getId(),
                reminder.getUserId(),
                reminder.getStatus(),
                Map.of(
                        "event", event,
                        "title", reminder.getTitle(),
                        "level", reminder.getEscalationLevel(),
                        "channel", result.getChannel()));
        if (reminder.getEscalationLevel() < 3) {
            int minutes =
                    reminder.getEscalationLevel() == 1
                            ? settings.getReminderEscalationMinutes()
                            : settings.getReminderFinalMinutes();
            scheduler.schedule(
                    dueJobId(reminder.getId()),
                    clock.now().plusMinutes(minutes),
                    kwargs -> notifyDue(((Number) kwargs.get("reminder_id")).intValue()),
                    Map.of("reminder_id", reminder.getId()));
        } else {
            scheduler.schedule(
                    missJobId(reminder.getId()),
                    clock.now().plusMinutes(settings.getReminderMissedMinutes() - settings.getReminderFinalMinutes()),
                    kwargs -> markMissed(((Number) kwargs.get("reminder_id")).intValue()),
                    Map.of("reminder_id", reminder.getId()));
        }
    }

    @Transactional
    public void markMissed(int reminderId) {
        ReminderEntity reminder = reminders.findById(reminderId).orElse(null);
        if (reminder == null || "completed".equals(reminder.getStatus())) return;
        reminder.setStatus("missed");
        reminder.setUpdatedAt(clock.now());
        reminders.save(reminder);
        emitter.emit(
                "reminder",
                reminder.getId(),
                reminder.getUserId(),
                "missed",
                Map.of("event", "missed", "title", reminder.getTitle()));
        controller.spawnNext(reminder);
    }
}
