package com.riseup.history;

import com.riseup.api.ApiMapper;
import com.riseup.clock.Clock;
import com.riseup.domain.AlarmLogEntity;
import com.riseup.domain.EscalationLogEntity;
import com.riseup.domain.ReminderLogEntity;
import com.riseup.error.NotFoundException;
import com.riseup.error.ValidationFailedException;
import com.riseup.repository.AlarmLogRepository;
import com.riseup.repository.EscalationLogRepository;
import com.riseup.repository.ReminderLogRepository;
import com.riseup.time.TimeUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HistoryService {
    private final AlarmLogRepository alarmLogs;
    private final ReminderLogRepository reminderLogs;
    private final EscalationLogRepository escalationLogs;
    private final Clock clock;

    public HistoryService(
            AlarmLogRepository alarmLogs,
            ReminderLogRepository reminderLogs,
            EscalationLogRepository escalationLogs,
            Clock clock) {
        this.alarmLogs = alarmLogs;
        this.reminderLogs = reminderLogs;
        this.escalationLogs = escalationLogs;
        this.clock = clock;
    }

    public Map<String, Object> list(int userId, String type, Integer sinceDays, int page, int pageSize) {
        LocalDateTime since = sinceDays == null ? clock.now().minusDays(90) : clock.now().minusDays(sinceDays);
        List<Map<String, Object>> items = new ArrayList<>();
        if (type == null || "alarm".equals(type)) {
            for (AlarmLogEntity l : alarmLogs.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", "alarm");
                m.put("id", l.getId());
                m.put("event", l.getEvent());
                m.put("created_at", TimeUtil.isoZ(l.getCreatedAt()));
                m.put("details", l.getDetails());
                items.add(m);
            }
        }
        if (type == null || "reminder".equals(type)) {
            for (ReminderLogEntity l : reminderLogs.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", "reminder");
                m.put("id", l.getId());
                m.put("event", l.getEvent());
                m.put("created_at", TimeUtil.isoZ(l.getCreatedAt()));
                m.put("details", l.getDetails());
                items.add(m);
            }
        }
        if (type == null || "escalation".equals(type)) {
            for (EscalationLogEntity l : escalationLogs.findByUserIdOrderByCreatedAtDesc(userId)) {
                if (l.getCreatedAt().isBefore(since)) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", "escalation");
                m.put("id", l.getId());
                m.put("event", l.getStatus());
                m.put("created_at", TimeUtil.isoZ(l.getCreatedAt()));
                m.put("details", ApiMapper.escalation(l));
                items.add(m);
            }
        }
        items.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("created_at"))).reversed());
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(items.size(), from + pageSize);
        List<Map<String, Object>> pageItems = from >= items.size() ? List.of() : items.subList(from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", pageItems);
        out.put("page", page);
        out.put("page_size", pageSize);
        out.put("total", items.size());
        return out;
    }

    public Map<String, Object> explain(int userId, String kind, int refId) {
        return switch (kind) {
            case "escalation" -> {
                EscalationLogEntity e =
                        escalationLogs
                                .findByIdAndUserId(refId, userId)
                                .orElseThrow(() -> new NotFoundException("Escalation " + refId + " not found"));
                Map<String, Object> m = new LinkedHashMap<>(ApiMapper.escalation(e));
                m.put("kind", "escalation");
                m.put("explanation", e.getReasoning());
                yield m;
            }
            case "alarm" -> {
                AlarmLogEntity l =
                        alarmLogs.findById(refId).filter(a -> a.getUserId().equals(userId))
                                .orElseThrow(() -> new NotFoundException("Alarm log " + refId + " not found"));
                yield Map.of("kind", "alarm", "id", l.getId(), "event", l.getEvent(), "details", l.getDetails());
            }
            case "reminder" -> {
                ReminderLogEntity l =
                        reminderLogs.findById(refId).filter(a -> a.getUserId().equals(userId))
                                .orElseThrow(() -> new NotFoundException("Reminder log " + refId + " not found"));
                yield Map.of("kind", "reminder", "id", l.getId(), "event", l.getEvent(), "details", l.getDetails());
            }
            default -> throw new ValidationFailedException("Unknown history kind '" + kind + "'");
        };
    }
}
