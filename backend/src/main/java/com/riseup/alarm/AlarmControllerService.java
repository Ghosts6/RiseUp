package com.riseup.alarm;

import com.riseup.clock.Clock;
import com.riseup.config.RiseUpProperties;
import com.riseup.domain.AlarmEntity;
import com.riseup.domain.CheckInEntity;
import com.riseup.domain.EscalationLogEntity;
import com.riseup.domain.UserPreferencesEntity;
import com.riseup.error.ConflictException;
import com.riseup.error.LimitReachedException;
import com.riseup.error.ValidationFailedException;
import com.riseup.agent.EscalationEngine;
import com.riseup.repository.AlarmRepository;
import com.riseup.repository.EscalationLogRepository;
import com.riseup.repository.UserPreferencesRepository;
import com.riseup.time.TimeUtil;
import com.riseup.verification.VerificationResponse;
import com.riseup.verification.VerificationResult;
import com.riseup.verification.VerificationTask;
import com.riseup.verification.VerificationTaskFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlarmControllerService {
    private static final Set<String> EDITABLE =
            Set.of(
                    "time",
                    "label",
                    "sound",
                    "vibration",
                    "repeat_pattern",
                    "snooze_enabled",
                    "verification_task_type",
                    "custom_task_text",
                    "priority");

    private final AlarmRepository alarms;
    private final UserPreferencesRepository prefsRepo;
    private final EscalationLogRepository escalations;
    private final AlarmSchedulerService alarmScheduler;
    private final CheckInService checkins;
    private final EscalationEngine escalationEngine;
    private final VerificationTaskFactory taskFactory;
    private final RiseUpProperties settings;
    private final Clock clock;
    private final StatusEmitter emitter;

    public AlarmControllerService(
            AlarmRepository alarms,
            UserPreferencesRepository prefsRepo,
            EscalationLogRepository escalations,
            AlarmSchedulerService alarmScheduler,
            CheckInService checkins,
            EscalationEngine escalationEngine,
            VerificationTaskFactory taskFactory,
            RiseUpProperties settings,
            Clock clock,
            StatusEmitter emitter) {
        this.alarms = alarms;
        this.prefsRepo = prefsRepo;
        this.escalations = escalations;
        this.alarmScheduler = alarmScheduler;
        this.checkins = checkins;
        this.escalationEngine = escalationEngine;
        this.taskFactory = taskFactory;
        this.settings = settings;
        this.clock = clock;
        this.emitter = emitter;
    }

    public List<AlarmEntity> listAlarms(int userId) {
        return alarms.findByUserIdOrderByTimeAsc(userId);
    }

    public AlarmEntity get(int userId, int alarmId) {
        return alarmScheduler.require(alarmId, userId);
    }

    @Transactional
    public AlarmEntity createAlarm(int userId, Map<String, Object> data) {
        if (alarms.countByUserId(userId) >= settings.getMaxAlarmsPerUser()) {
            throw new LimitReachedException("Maximum alarms reached. Delete an alarm to add another.");
        }
        UserPreferencesEntity prefs =
                prefsRepo.findById(userId).orElseGet(() -> {
                    UserPreferencesEntity p = new UserPreferencesEntity();
                    p.setUserId(userId);
                    return prefsRepo.save(p);
                });
        AlarmEntity alarm = new AlarmEntity();
        alarm.setUserId(userId);
        alarm.setLabel(str(data.get("label"), "Alarm"));
        alarm.setSound(str(data.get("sound"), "classic"));
        alarm.setVibration(str(data.get("vibration"), "medium"));
        alarm.setRepeatPattern(str(data.get("repeat_pattern"), "once"));
        alarm.setSnoozeEnabled(bool(data.get("snooze_enabled"), false));
        alarm.setVerificationTaskType(str(data.get("verification_task_type"), prefs.getDefaultTaskType()));
        alarm.setCustomTaskText(data.get("custom_task_text") == null ? null : String.valueOf(data.get("custom_task_text")));
        alarm.setPriority(data.get("priority") instanceof Number n ? n.intValue() : 0);
        alarm.setActive(data.get("is_active") instanceof Boolean b ? b : true);
        alarm.setTime(str(data.get("time"), null));
        alarm.setCreatedAt(clock.now());
        alarm.setUpdatedAt(clock.now());
        validate(alarm);
        alarms.save(alarm);
        alarmScheduler.scheduleAlarm(alarm);
        return alarm;
    }

    @Transactional
    public AlarmEntity editAlarm(int userId, int alarmId, Map<String, Object> data) {
        AlarmEntity alarm = get(userId, alarmId);
        if (alarm.isRunning()) throw new ConflictException("This alarm is running; edit it after it finishes.");
        applyEditable(alarm, data);
        validate(alarm);
        alarm.setUpdatedAt(clock.now());
        alarmScheduler.scheduleAlarm(alarm);
        return alarm;
    }

    @Transactional
    public AlarmEntity toggle(int userId, int alarmId, Boolean active) {
        AlarmEntity alarm = get(userId, alarmId);
        if (alarm.isRunning() && Boolean.FALSE.equals(active)) {
            throw new ConflictException("A running alarm cannot be disabled; complete the challenge first.");
        }
        alarm.setActive(active == null ? !alarm.isActive() : active);
        alarmScheduler.scheduleAlarm(alarm);
        return alarm;
    }

    @Transactional
    public void delete(int userId, int alarmId) {
        AlarmEntity alarm = get(userId, alarmId);
        if (alarm.isRunning()) {
            throw new ConflictException("A running alarm cannot be deleted; complete the challenge first.");
        }
        alarmScheduler.cancelAlarm(alarm);
        alarms.delete(alarm);
    }

    @Transactional
    public AlarmEntity triggerNow(int userId, int alarmId) {
        get(userId, alarmId);
        AlarmEntity result = alarmScheduler.triggerAlarm(alarmId, true);
        return result != null ? result : get(userId, alarmId);
    }

    public Map<String, Object> getChallenge(int userId, int alarmId) {
        AlarmEntity alarm = get(userId, alarmId);
        if (alarm.getCurrentTask() == null) throw new ConflictException("Alarm is not ringing");
        VerificationTask task = taskFactory.fromState(alarm.getCurrentTask());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alarm_id", alarm.getId());
        out.put("status", alarm.getStatus());
        out.put("label", alarm.getLabel());
        out.put("sound", alarm.getSound());
        out.put("vibration", alarm.getVibration());
        out.put("triggered_at", TimeUtil.isoZ(alarm.getTriggeredAt()));
        out.put(
                "snooze_available",
                alarm.isSnoozeEnabled()
                        && alarm.getSnoozeCount() < settings.getMaxSnoozes()
                        && "ringing".equals(alarm.getStatus()));
        out.put("challenge", task.getTaskPrompt());
        return out;
    }

    @Transactional
    public Map<String, Object> verify(int userId, int alarmId, VerificationResponse response) {
        AlarmEntity alarm = get(userId, alarmId);
        if (alarm.getCurrentTask() == null
                || !("ringing".equals(alarm.getStatus()) || "escalating".equals(alarm.getStatus()))) {
            throw new ConflictException("Alarm is not ringing");
        }
        VerificationTask task = taskFactory.fromState(alarm.getCurrentTask());
        VerificationResult result = task.validate(response);
        if (!result.isSuccess()) {
            alarm.setFailedAttempts(alarm.getFailedAttempts() + 1);
            String message = result.getMessage();
            VerificationTask newTask = null;
            if (result.isCameraError()) {
                alarm.setCameraFailures(alarm.getCameraFailures() + 1);
                if (alarm.getCameraFailures() >= VerificationTaskFactory.MAX_CAMERA_FAILURES) {
                    newTask =
                            taskFactory.create(
                                    alarm.getVerificationTaskType(),
                                    alarm.getCustomTaskText(),
                                    alarm.getCameraFailures());
                    message = "Camera unavailable. Switched to a math puzzle instead.";
                }
            } else if (task.resetsOnFailure()) {
                newTask = taskFactory.create(task.getTaskType(), alarm.getCustomTaskText(), 0);
            }
            if (newTask != null) alarm.setCurrentTask(newTask.toState());
            alarms.save(alarm);
            alarmScheduler.addLog(
                    alarm,
                    "failed_attempt",
                    Map.of("attempts", alarm.getFailedAttempts(), "camera_error", result.isCameraError()));
            VerificationTask current = newTask != null ? newTask : task;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", false);
            out.put("message", message);
            out.put("challenge", current.getTaskPrompt());
            out.put("attempts", alarm.getFailedAttempts());
            return out;
        }
        LocalDateTime now = clock.now();
        Double seconds =
                alarm.getTriggeredAt() == null
                        ? null
                        : (double) Duration.between(alarm.getTriggeredAt(), now).getSeconds();
        alarmScheduler.addLog(
                alarm,
                "verified",
                Map.of(
                        "seconds_to_verify", seconds == null ? 0 : seconds,
                        "attempts", alarm.getFailedAttempts() + 1));
        if ("escalating".equals(alarm.getStatus())) {
            escalationEngine.acknowledge(alarm, "verification");
        }
        CheckInEntity checkin = checkins.schedule(alarm);
        if (checkin == null) {
            alarmScheduler.finish(alarm, "completed");
        } else {
            alarmScheduler.cancelAlarmTimeout(alarm.getId());
            alarm.setCurrentTask(null);
            alarm.setStatus("awaiting_checkin");
            alarms.save(alarm);
            emitter.emit(
                    "alarm",
                    alarm.getId(),
                    alarm.getUserId(),
                    "awaiting_checkin",
                    Map.of("label", alarm.getLabel(), "time", alarm.getTime()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Task completed! Rest well.");
        out.put("checkin_at", checkin == null ? null : TimeUtil.isoZ(checkin.getScheduledAt()));
        out.put("seconds_to_verify", seconds);
        return out;
    }

    @Transactional
    public AlarmEntity snooze(int userId, int alarmId) {
        return alarmScheduler.snooze(get(userId, alarmId));
    }

    public Map<String, Object> escalationStatus(int userId, int alarmId) {
        AlarmEntity alarm = get(userId, alarmId);
        List<EscalationLogEntity> logs = escalations.findByAlarmIdOrderByCreatedAtAsc(alarm.getId());
        List<EscalationLogEntity> current;
        if (alarm.getEscalationEpisode() != null) {
            current =
                    logs.stream()
                            .filter(e -> alarm.getEscalationEpisode().equals(e.getEpisode()))
                            .toList();
        } else {
            current = logs.size() <= 5 ? logs : logs.subList(logs.size() - 5, logs.size());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alarm_id", alarm.getId());
        out.put("status", alarm.getStatus());
        out.put("episode", alarm.getEscalationEpisode());
        out.put("attempts", current);
        return out;
    }

    private void applyEditable(AlarmEntity alarm, Map<String, Object> data) {
        if (data.containsKey("time") && data.get("time") != null) alarm.setTime(String.valueOf(data.get("time")));
        if (data.containsKey("label") && data.get("label") != null) alarm.setLabel(String.valueOf(data.get("label")));
        if (data.containsKey("sound") && data.get("sound") != null) alarm.setSound(String.valueOf(data.get("sound")));
        if (data.containsKey("vibration") && data.get("vibration") != null)
            alarm.setVibration(String.valueOf(data.get("vibration")));
        if (data.containsKey("repeat_pattern") && data.get("repeat_pattern") != null)
            alarm.setRepeatPattern(String.valueOf(data.get("repeat_pattern")));
        if (data.containsKey("snooze_enabled") && data.get("snooze_enabled") != null)
            alarm.setSnoozeEnabled(Boolean.TRUE.equals(data.get("snooze_enabled")));
        if (data.containsKey("verification_task_type") && data.get("verification_task_type") != null)
            alarm.setVerificationTaskType(String.valueOf(data.get("verification_task_type")));
        if (data.containsKey("custom_task_text"))
            alarm.setCustomTaskText(
                    data.get("custom_task_text") == null ? null : String.valueOf(data.get("custom_task_text")));
        if (data.containsKey("priority") && data.get("priority") instanceof Number n) alarm.setPriority(n.intValue());
    }

    private void validate(AlarmEntity alarm) {
        if (!TimeUtil.isValidHhmm(alarm.getTime())) {
            throw new ValidationFailedException("Please enter time in HH:MM format", "time");
        }
        if (!AlarmEntity.VIBRATION_LEVELS.contains(alarm.getVibration())) {
            throw new ValidationFailedException("Vibration must be low, medium, or high", "vibration");
        }
        if (!AlarmEntity.TASK_TYPES.contains(alarm.getVerificationTaskType())) {
            throw new ValidationFailedException(
                    "Task type must be one of math, trivia, object_detection, custom", "verification_task_type");
        }
        if ("custom".equals(alarm.getVerificationTaskType())
                && (alarm.getCustomTaskText() == null || alarm.getCustomTaskText().isBlank())) {
            throw new ValidationFailedException("Custom tasks need a description", "custom_task_text");
        }
        if (alarm.getLabel() == null || alarm.getLabel().isBlank()) alarm.setLabel("Alarm");
        TimeUtil.validateRepeat(alarm.getRepeatPattern());
    }

    private static String str(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }

    private static boolean bool(Object v, boolean def) {
        return v instanceof Boolean b ? b : def;
    }
}
