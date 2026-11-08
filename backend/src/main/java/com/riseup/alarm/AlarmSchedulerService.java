package com.riseup.alarm;

import com.riseup.clock.Clock;
import com.riseup.config.RiseUpProperties;
import com.riseup.domain.AlarmEntity;
import com.riseup.domain.AlarmLogEntity;
import com.riseup.domain.UserEntity;
import com.riseup.error.ConflictException;
import com.riseup.error.LimitReachedException;
import com.riseup.error.NotFoundException;
import com.riseup.repository.AlarmLogRepository;
import com.riseup.repository.AlarmRepository;
import com.riseup.repository.UserRepository;
import com.riseup.scheduling.JobScheduler;
import com.riseup.time.TimeUtil;
import com.riseup.verification.VerificationTask;
import com.riseup.verification.VerificationTaskFactory;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlarmSchedulerService {
    private final AlarmRepository alarms;
    private final AlarmLogRepository alarmLogs;
    private final UserRepository users;
    private final JobScheduler scheduler;
    private final Clock clock;
    private final RiseUpProperties settings;
    private final VerificationTaskFactory taskFactory;
    private final StatusEmitter emitter;
    private final AgentKick agentKick;

    public AlarmSchedulerService(
            AlarmRepository alarms,
            AlarmLogRepository alarmLogs,
            UserRepository users,
            JobScheduler scheduler,
            Clock clock,
            RiseUpProperties settings,
            VerificationTaskFactory taskFactory,
            StatusEmitter emitter,
            @Lazy AgentKick agentKick) {
        this.alarms = alarms;
        this.alarmLogs = alarmLogs;
        this.users = users;
        this.scheduler = scheduler;
        this.clock = clock;
        this.settings = settings;
        this.taskFactory = taskFactory;
        this.emitter = emitter;
        this.agentKick = agentKick;
    }

    public static String triggerJobId(int id) { return "alarm:" + id; }
    public static String snoozeJobId(int id) { return "alarm-snooze:" + id; }
    public static String timeoutJobId(int id) { return "alarm-timeout:" + id; }

    @Transactional
    public void scheduleAlarm(AlarmEntity alarm) {
        if (!alarm.isActive()) {
            cancelAlarm(alarm);
            alarm.setNextTriggerAt(null);
            if (!alarm.isRunning()) alarm.setStatus("disabled");
            alarms.save(alarm);
            return;
        }
        UserEntity user = users.findById(alarm.getUserId()).orElseThrow();
        alarm.setNextTriggerAt(
                TimeUtil.nextAlarmOccurrence(alarm.getTime(), alarm.getRepeatPattern(), user.getTimezone(), clock.now()));
        if (!alarm.isRunning()) alarm.setStatus("scheduled");
        alarms.save(alarm);
        scheduler.schedule(
                triggerJobId(alarm.getId()),
                alarm.getNextTriggerAt(),
                kwargs -> triggerAlarm(((Number) kwargs.get("alarm_id")).intValue(), false),
                Map.of("alarm_id", alarm.getId()));
    }

    public void cancelAlarmTimeout(int alarmId) {
        scheduler.cancel(timeoutJobId(alarmId));
    }

    public void cancelAlarm(AlarmEntity alarm) {
        scheduler.cancel(triggerJobId(alarm.getId()));
        scheduler.cancel(snoozeJobId(alarm.getId()));
        scheduler.cancel(timeoutJobId(alarm.getId()));
    }

    @Transactional
    public AlarmEntity triggerAlarm(int alarmId, boolean force) {
        AlarmEntity alarm = alarms.findById(alarmId).orElse(null);
        if (alarm == null || (!alarm.isActive() && !force)) return null;
        if (ListOf(alarm.getStatus(), "ringing", "escalating", "awaiting_checkin")) return alarm;
        boolean snoozed = "snoozed".equals(alarm.getStatus());
        if (!snoozed) {
            alarm.setSnoozeCount(0);
            alarm.setCameraFailures(0);
            alarm.setEscalationEpisode(null);
        }
        VerificationTask task =
                taskFactory.create(alarm.getVerificationTaskType(), alarm.getCustomTaskText(), alarm.getCameraFailures());
        alarm.setTriggeredAt(clock.now());
        alarm.setCurrentTask(task.toState());
        alarm.setFailedAttempts(0);
        alarm.setStatus("ringing");
        alarm.setUpdatedAt(clock.now());
        alarms.save(alarm);
        addLog(alarm, "triggered", Map.of("snoozed", snoozed));
        emitter.emit("alarm", alarm.getId(), alarm.getUserId(), "ringing", Map.of("label", alarm.getLabel(), "time", alarm.getTime()));
        scheduler.schedule(
                timeoutJobId(alarm.getId()),
                clock.now().plusSeconds(settings.getVerificationTimeoutSeconds()),
                kwargs -> timeoutHandler(((Number) kwargs.get("alarm_id")).intValue()),
                Map.of("alarm_id", alarm.getId()));
        if (!snoozed && !"once".equals(alarm.getRepeatPattern()) && alarm.isActive()) {
            scheduleAlarm(alarm);
        } else {
            alarm.setNextTriggerAt(null);
            alarms.save(alarm);
        }
        return alarm;
    }

    private static boolean ListOf(String status, String... vals) {
        for (String v : vals) if (v.equals(status)) return true;
        return false;
    }

    @Transactional
    public void timeoutHandler(int alarmId) {
        AlarmEntity alarm = alarms.findById(alarmId).orElse(null);
        if (alarm == null || !"ringing".equals(alarm.getStatus())) return;
        addLog(alarm, "timeout", Map.of("seconds", settings.getVerificationTimeoutSeconds()));
        startEscalation(alarm, "verification_timeout");
    }

    @Transactional
    public void startEscalation(AlarmEntity alarm, String trigger) {
        if ("escalating".equals(alarm.getStatus())) return;
        alarm.setEscalationEpisode(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        alarm.setStatus("escalating");
        alarm.setUpdatedAt(clock.now());
        alarms.save(alarm);
        Map<String, Object> payload = new HashMap<>();
        payload.put("trigger", trigger);
        payload.put("episode", alarm.getEscalationEpisode());
        payload.put("label", alarm.getLabel());
        payload.put("time", alarm.getTime());
        emitter.emit("alarm", alarm.getId(), alarm.getUserId(), "escalating", payload);
        agentKick.runEscalation(alarm.getId(), alarm.getEscalationEpisode(), trigger);
    }

    @Transactional
    public AlarmEntity snooze(AlarmEntity alarm) {
        if (!"ringing".equals(alarm.getStatus())) throw new ConflictException("Only a ringing alarm can be snoozed");
        if (!alarm.isSnoozeEnabled()) throw new ConflictException("Snooze is disabled for this alarm");
        if (alarm.getSnoozeCount() >= settings.getMaxSnoozes()) {
            throw new LimitReachedException("Snooze limit reached (" + settings.getMaxSnoozes() + ")");
        }
        alarm.setSnoozeCount(alarm.getSnoozeCount() + 1);
        scheduler.cancel(timeoutJobId(alarm.getId()));
        alarm.setCurrentTask(null);
        alarm.setStatus("snoozed");
        alarms.save(alarm);
        emitter.emit("alarm", alarm.getId(), alarm.getUserId(), "snoozed", Map.of("label", alarm.getLabel()));
        addLog(alarm, "snoozed", Map.of("count", alarm.getSnoozeCount()));
        LocalDateTime resumeAt = clock.now().plusMinutes(settings.getSnoozeMinutes());
        scheduler.schedule(
                snoozeJobId(alarm.getId()),
                resumeAt,
                kwargs -> triggerAlarm(((Number) kwargs.get("alarm_id")).intValue(), false),
                Map.of("alarm_id", alarm.getId()));
        return alarm;
    }

    @Transactional
    public void finish(AlarmEntity alarm, String status) {
        scheduler.cancel(timeoutJobId(alarm.getId()));
        alarm.setSnoozeCount(0);
        alarm.setCurrentTask(null);
        alarm.setStatus(status);
        alarm.setUpdatedAt(clock.now());
        if ("once".equals(alarm.getRepeatPattern())) {
            alarm.setActive(false);
            alarm.setNextTriggerAt(null);
        }
        alarms.save(alarm);
        emitter.emit("alarm", alarm.getId(), alarm.getUserId(), status, Map.of("label", alarm.getLabel(), "time", alarm.getTime()));
    }

    public void addLog(AlarmEntity alarm, String event, Map<String, Object> details) {
        AlarmLogEntity log = new AlarmLogEntity();
        log.setAlarmId(alarm.getId());
        log.setUserId(alarm.getUserId());
        log.setEvent(event);
        log.setTaskType(alarm.getVerificationTaskType());
        log.setDetails(details == null ? Map.of() : new HashMap<>(details));
        log.setCreatedAt(clock.now());
        alarmLogs.save(log);
    }

    public AlarmEntity require(int alarmId, int userId) {
        return alarms.findByIdAndUserId(alarmId, userId)
                .orElseThrow(() -> new NotFoundException("Alarm " + alarmId + " not found"));
    }
}
