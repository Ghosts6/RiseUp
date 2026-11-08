package com.riseup.insights;

import com.riseup.clock.Clock;
import com.riseup.domain.AlarmLogEntity;
import com.riseup.domain.EscalationLogEntity;
import com.riseup.repository.AlarmLogRepository;
import com.riseup.repository.EscalationLogRepository;
import com.riseup.repository.ReminderLogRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private final AlarmLogRepository alarmLogs;
    private final EscalationLogRepository escalationLogs;
    private final ReminderLogRepository reminderLogs;
    private final Clock clock;

    public AnalyticsService(
            AlarmLogRepository alarmLogs,
            EscalationLogRepository escalationLogs,
            ReminderLogRepository reminderLogs,
            Clock clock) {
        this.alarmLogs = alarmLogs;
        this.escalationLogs = escalationLogs;
        this.reminderLogs = reminderLogs;
        this.clock = clock;
    }

    public Map<String, Object> getStats(int userId, int days) {
        LocalDateTime since = clock.now().minusDays(days);
        List<AlarmLogEntity> alarms = alarmLogs.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since);
        List<EscalationLogEntity> esc = escalationLogs.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(e -> e.getCreatedAt().isAfter(since))
                .toList();
        long verified = alarms.stream().filter(a -> "verified".equals(a.getEvent())).count();
        long triggered = alarms.stream().filter(a -> "triggered".equals(a.getEvent())).count();
        long missed = alarms.stream().filter(a -> "missed".equals(a.getEvent())).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("range_days", days);
        out.put("alarms_triggered", triggered);
        out.put("alarms_verified", verified);
        out.put("alarms_missed", missed);
        out.put("escalations", esc.size());
        out.put("verification_rate", triggered == 0 ? 0.0 : (double) verified / triggered);
        out.put("reminder_events", reminderLogs.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since).size());
        return out;
    }
}
