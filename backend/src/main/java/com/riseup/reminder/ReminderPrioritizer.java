package com.riseup.reminder;

import com.riseup.domain.ReminderEntity;
import com.riseup.domain.UserProfileEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReminderPrioritizer {
    public record Result(List<Integer> order, String reasoning, String decidedBy, boolean fallbackUsed) {}

    public Result prioritize(
            List<ReminderEntity> due, UserProfileEntity profile, LocalDateTime now, String tz) {
        Map<String, Integer> rank = Map.of("critical", 0, "high", 1, "medium", 2, "low", 3);
        List<ReminderEntity> ordered = new ArrayList<>(due);
        ordered.sort(
                Comparator.comparingInt((ReminderEntity r) -> rank.getOrDefault(r.getPriority(), 2))
                        .thenComparing(ReminderEntity::getDueAt));
        String reasoning =
                ordered.isEmpty()
                        ? "No reminders due"
                        : "'"
                                + ordered.get(0).getTitle()
                                + "' goes first: "
                                + ordered.get(0).getPriority()
                                + " priority; the rest follow by priority then due time.";
        return new Result(ordered.stream().map(ReminderEntity::getId).toList(), reasoning, "rules", false);
    }
}
