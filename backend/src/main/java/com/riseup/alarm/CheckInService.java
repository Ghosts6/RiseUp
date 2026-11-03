package com.riseup.alarm;

import com.riseup.agent.EscalationEngine;
import com.riseup.clock.Clock;
import com.riseup.config.RiseUpProperties;
import com.riseup.domain.AlarmEntity;
import com.riseup.domain.CheckInEntity;
import com.riseup.domain.UserEntity;
import com.riseup.domain.UserPreferencesEntity;
import com.riseup.error.NotFoundException;
import com.riseup.repository.AlarmRepository;
import com.riseup.repository.CheckInRepository;
import com.riseup.repository.UserPreferencesRepository;
import com.riseup.repository.UserRepository;
import com.riseup.scheduling.JobScheduler;
import com.riseup.tools.ToolManager;
import com.riseup.tools.ToolResult;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckInService {
    private final CheckInRepository checkins;
    private final AlarmRepository alarms;
    private final UserRepository users;
    private final UserPreferencesRepository prefsRepo;
    private final JobScheduler scheduler;
    private final Clock clock;
    private final RiseUpProperties settings;
    private final ToolManager tools;
    private final AlarmSchedulerService alarmScheduler;
    private final EscalationEngine escalationEngine;
    private final StatusEmitter emitter;

    public CheckInService(
            CheckInRepository checkins,
            AlarmRepository alarms,
            UserRepository users,
            UserPreferencesRepository prefsRepo,
            JobScheduler scheduler,
            Clock clock,
            RiseUpProperties settings,
            ToolManager tools,
            @Lazy AlarmSchedulerService alarmScheduler,
            @Lazy EscalationEngine escalationEngine,
            StatusEmitter emitter) {
        this.checkins = checkins;
        this.alarms = alarms;
        this.users = users;
        this.prefsRepo = prefsRepo;
        this.scheduler = scheduler;
        this.clock = clock;
        this.settings = settings;
        this.tools = tools;
        this.alarmScheduler = alarmScheduler;
        this.escalationEngine = escalationEngine;
        this.emitter = emitter;
    }

    public static String sendJobId(int id) { return "checkin-send:" + id; }
    public static String timeoutJobId(int id) { return "checkin-timeout:" + id; }

    @Transactional
    public CheckInEntity schedule(AlarmEntity alarm) {
        UserPreferencesEntity prefs =
                prefsRepo.findById(alarm.getUserId()).orElseGet(() -> {
                    UserPreferencesEntity p = new UserPreferencesEntity();
                    p.setUserId(alarm.getUserId());
                    return prefsRepo.save(p);
                });
        LocalDateTime now = clock.now();
        if (!prefs.isCheckinEnabled()) {
            CheckInEntity skipped = new CheckInEntity();
            skipped.setAlarmId(alarm.getId());
            skipped.setUserId(alarm.getUserId());
            skipped.setScheduledAt(now);
            skipped.setStatus("skipped");
            skipped.setCreatedAt(now);
            checkins.save(skipped);
            alarmScheduler.addLog(alarm, "checkin_skipped", Map.of());
            return null;
        }
        int delay = Math.min(Math.max(prefs.getCheckinDelayMinutes(), 5), 10);
        CheckInEntity checkin = new CheckInEntity();
        checkin.setAlarmId(alarm.getId());
        checkin.setUserId(alarm.getUserId());
        checkin.setScheduledAt(now.plusMinutes(delay));
        checkin.setStatus("scheduled");
        checkin.setCreatedAt(now);
        checkins.save(checkin);
        scheduler.schedule(
                sendJobId(checkin.getId()),
                checkin.getScheduledAt(),
                kwargs -> send(((Number) kwargs.get("checkin_id")).intValue()),
                Map.of("checkin_id", checkin.getId()));
        return checkin;
    }

    @Transactional
    public CheckInEntity send(int checkinId) {
        CheckInEntity checkin = checkins.findById(checkinId).orElse(null);
        if (checkin == null || !"scheduled".equals(checkin.getStatus())) return null;
        UserEntity user = users.findById(checkin.getUserId()).orElseThrow();
        UserPreferencesEntity prefs = prefsRepo.findById(user.getId()).orElseThrow();
        ToolResult result =
                tools.sendPushNotification(user, prefs, "Are you still awake?", "Tap to confirm you're up.");
        if (!result.isSuccess()) {
            result = tools.sendSms(user.getPhone(), "RiseUp check-in: Reply YES to confirm you're awake.");
        }
        LocalDateTime now = clock.now();
        checkin.setSentAt(now);
        checkin.setChannel(result.isSuccess() ? result.getChannel() : "dashboard");
        checkin.setStatus("sent");
        checkins.save(checkin);
        emitter.emit(
                "checkin",
                checkin.getId(),
                checkin.getUserId(),
                "sent",
                Map.of("channel", checkin.getChannel(), "delivered", result.isSuccess()));
        scheduler.schedule(
                timeoutJobId(checkin.getId()),
                now.plusMinutes(settings.getCheckinResponseMinutes()),
                kwargs -> timeoutHandler(((Number) kwargs.get("checkin_id")).intValue()),
                Map.of("checkin_id", checkin.getId()));
        return checkin;
    }

    @Transactional
    public CheckInEntity recordResponse(int userId) {
        CheckInEntity checkin =
                checkins
                        .findFirstByUserIdAndStatusInOrderByScheduledAtDesc(
                                userId, List.of("scheduled", "sent", "missed"))
                        .orElseThrow(() -> new NotFoundException("No pending check-in"));
        LocalDateTime now = clock.now();
        scheduler.cancel(sendJobId(checkin.getId()));
        scheduler.cancel(timeoutJobId(checkin.getId()));
        boolean late = "missed".equals(checkin.getStatus());
        checkin.setRespondedAt(now);
        LocalDateTime reference = checkin.getSentAt() != null ? checkin.getSentAt() : checkin.getScheduledAt();
        double responseSeconds = Math.max(Duration.between(reference, now).getSeconds(), 0);
        checkin.setStatus(late ? "late" : "confirmed");
        checkins.save(checkin);
        emitter.emit(
                "checkin",
                checkin.getId(),
                userId,
                checkin.getStatus(),
                Map.of("response_seconds", responseSeconds));
        if (checkin.getAlarmId() != null) {
            AlarmEntity alarm = alarms.findById(checkin.getAlarmId()).orElse(null);
            if (alarm != null) {
                alarmScheduler.addLog(
                        alarm,
                        late ? "checkin_late" : "checkin_confirmed",
                        Map.of("response_seconds", responseSeconds));
                if ("escalating".equals(alarm.getStatus())) {
                    escalationEngine.acknowledge(alarm, "checkin");
                }
                if ("awaiting_checkin".equals(alarm.getStatus()) || "escalating".equals(alarm.getStatus())) {
                    alarmScheduler.finish(alarm, "completed");
                }
            }
        }
        return checkin;
    }

    @Transactional
    public void timeoutHandler(int checkinId) {
        CheckInEntity checkin = checkins.findById(checkinId).orElse(null);
        if (checkin == null || !"sent".equals(checkin.getStatus())) return;
        checkin.setStatus("missed");
        checkins.save(checkin);
        emitter.emit("checkin", checkin.getId(), checkin.getUserId(), "missed", Map.of());
        if (checkin.getAlarmId() == null) return;
        AlarmEntity alarm = alarms.findById(checkin.getAlarmId()).orElse(null);
        if (alarm == null) return;
        alarmScheduler.addLog(alarm, "checkin_missed", Map.of());
        if ("awaiting_checkin".equals(alarm.getStatus())) {
            alarmScheduler.startEscalation(alarm, "checkin_missed");
        }
    }

    public List<CheckInEntity> listRecent(int userId) {
        return checkins.findByUserIdOrderByScheduledAtDesc(userId);
    }
}
