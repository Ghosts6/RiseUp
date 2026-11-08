package com.riseup.scheduling;

import com.riseup.alarm.AlarmSchedulerService;
import com.riseup.domain.AlarmEntity;
import com.riseup.domain.ReminderEntity;
import com.riseup.reminder.ReminderSchedulerService;
import com.riseup.repository.AlarmRepository;
import com.riseup.repository.ReminderRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Rebuild in-memory jobs from persisted next-trigger times after restart. */
@Component
@Profile("!test")
public class ScheduleRehydrateRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ScheduleRehydrateRunner.class);

    private final AlarmRepository alarms;
    private final ReminderRepository reminders;
    private final AlarmSchedulerService alarmScheduler;
    private final ReminderSchedulerService reminderScheduler;

    public ScheduleRehydrateRunner(
            AlarmRepository alarms,
            ReminderRepository reminders,
            AlarmSchedulerService alarmScheduler,
            ReminderSchedulerService reminderScheduler) {
        this.alarms = alarms;
        this.reminders = reminders;
        this.alarmScheduler = alarmScheduler;
        this.reminderScheduler = reminderScheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        int alarmCount = 0;
        for (AlarmEntity alarm : alarms.findAll()) {
            if (!alarm.isActive()) {
                continue;
            }
            if (alarm.isRunning()) {
                continue;
            }
            try {
                alarmScheduler.scheduleAlarm(alarm);
                alarmCount++;
            } catch (Exception e) {
                log.warn("Failed to rehydrate alarm {}: {}", alarm.getId(), e.getMessage());
            }
        }

        int reminderCount = 0;
        List<ReminderEntity> open = reminders.findAll().stream().filter(ReminderEntity::isOpen).toList();
        for (ReminderEntity reminder : open) {
            try {
                reminderScheduler.scheduleReminder(reminder);
                reminderCount++;
            } catch (Exception e) {
                log.warn("Failed to rehydrate reminder {}: {}", reminder.getId(), e.getMessage());
            }
        }
        log.info("Schedule rehydrate complete: {} alarms, {} reminders", alarmCount, reminderCount);
    }
}
