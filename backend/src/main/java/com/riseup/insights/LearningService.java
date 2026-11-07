package com.riseup.insights;

import com.riseup.clock.Clock;
import com.riseup.domain.AlarmLogEntity;
import com.riseup.domain.EscalationLogEntity;
import com.riseup.domain.ReminderLogEntity;
import com.riseup.domain.UserProfileEntity;
import com.riseup.repository.AlarmLogRepository;
import com.riseup.repository.EscalationLogRepository;
import com.riseup.repository.ReminderLogRepository;
import com.riseup.repository.UserProfileRepository;
import com.riseup.repository.UserRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {
    private final UserProfileRepository profiles;
    private final AlarmLogRepository alarmLogs;
    private final EscalationLogRepository escalationLogs;
    private final ReminderLogRepository reminderLogs;
    private final UserRepository users;
    private final Clock clock;

    public LearningService(
            UserProfileRepository profiles,
            AlarmLogRepository alarmLogs,
            EscalationLogRepository escalationLogs,
            ReminderLogRepository reminderLogs,
            UserRepository users,
            Clock clock) {
        this.profiles = profiles;
        this.alarmLogs = alarmLogs;
        this.escalationLogs = escalationLogs;
        this.reminderLogs = reminderLogs;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public UserProfileEntity analyzeBehavior(int userId) {
        UserProfileEntity profile =
                profiles.findById(userId).orElseGet(() -> {
                    UserProfileEntity p = new UserProfileEntity();
                    p.setUserId(userId);
                    return p;
                });

        List<AlarmLogEntity> logs = alarmLogs.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Object> verification = new HashMap<>();
        long verified = logs.stream().filter(l -> "verified".equals(l.getEvent())).count();
        long failed = logs.stream().filter(l -> "failed_attempt".equals(l.getEvent())).count();
        long triggered = logs.stream().filter(l -> "triggered".equals(l.getEvent())).count();
        long missed = logs.stream().filter(l -> "timeout".equals(l.getEvent()) || "missed".equals(l.getEvent())).count();
        verification.put("verified", verified);
        verification.put("failed_attempts", failed);
        verification.put("triggered", triggered);
        verification.put("missed", missed);
        profile.setVerificationStats(verification);

        Map<String, Object> alarmPatterns = new LinkedHashMap<>();
        Map<Integer, Long> hourCounts = new LinkedHashMap<>();
        for (AlarmLogEntity log : logs) {
            if (!"verified".equals(log.getEvent()) || log.getCreatedAt() == null) {
                continue;
            }
            int hour = log.getCreatedAt().getHour();
            hourCounts.merge(hour, 1L, Long::sum);
        }
        alarmPatterns.put("wake_hour_histogram", hourCounts);
        if (!hourCounts.isEmpty()) {
            int bestHour =
                    hourCounts.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(7);
            alarmPatterns.put("typical_wake_hour", bestHour);
        }
        profile.setAlarmPatterns(alarmPatterns);

        Map<String, Object> escalationStats = new LinkedHashMap<>();
        for (EscalationLogEntity e : escalationLogs.findByUserIdOrderByCreatedAtDesc(userId)) {
            String method = e.getMethod();
            if (method == null || method.isBlank()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> entry =
                    (Map<String, Object>) escalationStats.computeIfAbsent(method, k -> new LinkedHashMap<>());
            int attempts = ((Number) entry.getOrDefault("attempts", 0)).intValue() + 1;
            boolean success =
                    "confirmed".equalsIgnoreCase(e.getStatus())
                            || "answered".equalsIgnoreCase(e.getStatus())
                            || "success".equalsIgnoreCase(e.getStatus())
                            || e.getRespondedAt() != null;
            int successes = ((Number) entry.getOrDefault("successes", 0)).intValue() + (success ? 1 : 0);
            entry.put("attempts", attempts);
            entry.put("successes", successes);
            entry.put("success_rate", attempts == 0 ? 0.0 : (double) successes / attempts);
        }
        profile.setEscalationStats(escalationStats);

        Map<String, Object> taskRates = new LinkedHashMap<>();
        Map<String, long[]> byCategory = new LinkedHashMap<>();
        for (ReminderLogEntity rl : reminderLogs.findByUserIdOrderByCreatedAtDesc(userId)) {
            String category = rl.getCategory() == null ? "other" : rl.getCategory();
            long[] pair = byCategory.computeIfAbsent(category, k -> new long[2]);
            pair[0]++; // events
            if ("completed".equalsIgnoreCase(rl.getEvent()) || "done".equalsIgnoreCase(rl.getEvent())) {
                pair[1]++;
            }
        }
        for (Map.Entry<String, long[]> e : byCategory.entrySet()) {
            long total = e.getValue()[0];
            long done = e.getValue()[1];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("events", total);
            row.put("completed", done);
            row.put("completion_rate", total == 0 ? 0.0 : (double) done / total);
            taskRates.put(e.getKey(), row);
        }
        profile.setTaskCompletionRates(taskRates);

        int escalationAttempts = 0;
        for (Object v : escalationStats.values()) {
            if (v instanceof Map<?, ?> m && m.get("attempts") instanceof Number n) {
                escalationAttempts += n.intValue();
            }
        }
        int signal = logs.size() + escalationAttempts;
        profile.setDataStatus(signal < 5 ? "insufficient" : "ready");
        profile.setUpdatedAt(clock.now());
        return profiles.save(profile);
    }

    public void recomputeAllUsers() {
        users.findAll().forEach(u -> {
            try {
                analyzeBehavior(u.getId());
            } catch (Exception ignored) {
            }
        });
    }
}
