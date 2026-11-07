package com.riseup.agent;

import com.riseup.alarm.AlarmControllerService;
import com.riseup.api.ApiMapper;
import com.riseup.clock.Clock;
import com.riseup.domain.AlarmEntity;
import com.riseup.domain.CheckInEntity;
import com.riseup.domain.ReminderEntity;
import com.riseup.domain.UserEntity;
import com.riseup.repository.CheckInRepository;
import com.riseup.repository.UserRepository;
import com.riseup.reminder.ReminderControllerService;
import com.riseup.time.TimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class BriefingService {
    private static final Set<String> HIGH = Set.of("high", "critical");

    private final UserRepository users;
    private final AlarmControllerService alarms;
    private final ReminderControllerService reminders;
    private final CheckInRepository checkIns;
    private final Clock clock;

    public BriefingService(
            UserRepository users,
            AlarmControllerService alarms,
            ReminderControllerService reminders,
            CheckInRepository checkIns,
            Clock clock) {
        this.users = users;
        this.alarms = alarms;
        this.reminders = reminders;
        this.checkIns = checkIns;
        this.clock = clock;
    }

    public Map<String, Object> today(int userId) {
        UserEntity user = users.findById(userId).orElseThrow();
        String tz = user.getTimezone();
        LocalDateTime nowUtc = clock.now();
        LocalDate today = TimeUtil.toLocal(nowUtc, tz).toLocalDate();

        List<Map<String, Object>> alarmRows = new ArrayList<>();
        for (AlarmEntity a : alarms.listAlarms(userId)) {
            if (!a.isActive() && !"awaiting_checkin".equals(a.getStatus()) && !"ringing".equals(a.getStatus())) {
                continue;
            }
            boolean todayTrigger =
                    a.getNextTriggerAt() != null
                            && TimeUtil.toLocal(a.getNextTriggerAt(), tz).toLocalDate().equals(today);
            boolean activeNow =
                    Set.of("ringing", "escalating", "awaiting_checkin", "snoozed").contains(a.getStatus());
            if (todayTrigger || activeNow) {
                alarmRows.add(ApiMapper.alarm(a, tz));
            }
        }

        List<Map<String, Object>> reminderRows = new ArrayList<>();
        List<Map<String, Object>> highPriority = new ArrayList<>();
        for (ReminderEntity r : reminders.list(userId, "open", null, null)) {
            LocalDate dueDay = TimeUtil.toLocal(r.getDueAt(), tz).toLocalDate();
            if (dueDay.equals(today) || dueDay.isBefore(today)) {
                Map<String, Object> row = ApiMapper.reminder(r, tz);
                reminderRows.add(row);
                if (HIGH.contains(String.valueOf(r.getPriority()).toLowerCase())) {
                    highPriority.add(row);
                }
            }
        }

        List<Map<String, Object>> pendingCheckins = new ArrayList<>();
        for (CheckInEntity c : checkIns.findByUserIdOrderByScheduledAtDesc(userId)) {
            if ("pending".equals(c.getStatus()) || "sent".equals(c.getStatus())) {
                pendingCheckins.add(ApiMapper.checkin(c));
            }
            if (pendingCheckins.size() >= 5) {
                break;
            }
        }

        String summary =
                "You have "
                        + alarmRows.size()
                        + " alarm"
                        + (alarmRows.size() == 1 ? "" : "s")
                        + " and "
                        + reminderRows.size()
                        + " open task"
                        + (reminderRows.size() == 1 ? "" : "s")
                        + " for today"
                        + (highPriority.isEmpty()
                                ? "."
                                : ", including " + highPriority.size() + " high priority.");
        if (!pendingCheckins.isEmpty()) {
            summary += " " + pendingCheckins.size() + " check-in(s) still need confirmation.";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date_local", today.toString());
        out.put("timezone", tz);
        out.put("summary", summary);
        out.put("alarms", alarmRows);
        out.put("reminders", reminderRows);
        out.put("high_priority", highPriority);
        out.put("checkins_pending", pendingCheckins);
        out.put("generated_at", ApiMapper.iso(nowUtc));
        out.put("source", "rules");
        return out;
    }
}
