package com.riseup.api;

import com.riseup.domain.AlarmEntity;
import com.riseup.domain.CheckInEntity;
import com.riseup.domain.EmergencyContactEntity;
import com.riseup.domain.EscalationLogEntity;
import com.riseup.domain.NotificationEntity;
import com.riseup.domain.RecommendationEntity;
import com.riseup.domain.ReminderEntity;
import com.riseup.domain.UserEntity;
import com.riseup.domain.UserPreferencesEntity;
import com.riseup.domain.UserProfileEntity;
import com.riseup.time.TimeUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiMapper {
    private ApiMapper() {}

    public static String iso(LocalDateTime dt) {
        return TimeUtil.isoZ(dt);
    }

    public static Map<String, Object> user(UserEntity u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("timezone", u.getTimezone());
        return m;
    }

    public static Map<String, Object> alarm(AlarmEntity a, String tz) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("time", a.getTime());
        m.put("label", a.getLabel());
        m.put("sound", a.getSound());
        m.put("vibration", a.getVibration());
        m.put("repeat_pattern", a.getRepeatPattern());
        m.put("snooze_enabled", a.isSnoozeEnabled());
        m.put("verification_task_type", a.getVerificationTaskType());
        m.put("custom_task_text", a.getCustomTaskText());
        m.put("priority", a.getPriority());
        m.put("is_active", a.isActive());
        m.put("status", a.getStatus());
        m.put("next_trigger_at", iso(a.getNextTriggerAt()));
        m.put("next_trigger_local", TimeUtil.localFmt(a.getNextTriggerAt(), tz));
        m.put("triggered_at", iso(a.getTriggeredAt()));
        m.put("snooze_count", a.getSnoozeCount());
        m.put("escalation_episode", a.getEscalationEpisode());
        return m;
    }

    public static Map<String, Object> checkin(CheckInEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("alarm_id", c.getAlarmId());
        m.put("status", c.getStatus());
        m.put("scheduled_at", iso(c.getScheduledAt()));
        m.put("sent_at", iso(c.getSentAt()));
        m.put("responded_at", iso(c.getRespondedAt()));
        m.put("channel", c.getChannel());
        return m;
    }

    public static Map<String, Object> escalation(EscalationLogEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("alarm_id", e.getAlarmId());
        m.put("episode", e.getEpisode());
        m.put("attempt", e.getAttempt());
        m.put("trigger", e.getTriggerEvent());
        m.put("method", e.getMethod());
        m.put("decided_by", e.getDecidedBy());
        m.put("reasoning", e.getReasoning());
        m.put("confidence", e.getConfidence());
        m.put("fallback_method", e.getFallbackMethod());
        m.put("fallback_used", e.isFallbackUsed());
        m.put("status", e.getStatus());
        m.put("error", e.getError());
        m.put("created_at", iso(e.getCreatedAt()));
        m.put("sent_at", iso(e.getSentAt()));
        m.put("responded_at", iso(e.getRespondedAt()));
        m.put("response_time_seconds", e.getResponseTimeSeconds());
        return m;
    }

    public static Map<String, Object> reminder(ReminderEntity r, String tz) {
        List<LocalDateTime> extras = new ArrayList<>();
        for (Object value : r.getExtraTimes() == null ? List.of() : r.getExtraTimes()) {
            try {
                extras.add(LocalDateTime.parse(String.valueOf(value).replace("Z", "")));
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("title", r.getTitle());
        m.put("notes", r.getNotes());
        m.put("due_at", iso(r.getDueAt()));
        m.put("due_local", TimeUtil.localFmt(r.getDueAt(), tz));
        m.put("priority", r.getPriority());
        m.put("category", r.getCategory());
        m.put("repeat_pattern", r.getRepeatPattern());
        m.put("series_id", r.getSeriesId());
        m.put("notification_type", r.getNotificationType());
        m.put("extra_times", extras.stream().map(ApiMapper::iso).toList());
        m.put("extra_times_local", extras.stream().map(dt -> TimeUtil.localFmt(dt, tz)).toList());
        m.put("status", r.getStatus());
        m.put("escalation_level", r.getEscalationLevel());
        m.put("completed_at", iso(r.getCompletedAt()));
        m.put("snooze_count", r.getSnoozeCount());
        m.put("source", r.getSource());
        m.put("created_at", iso(r.getCreatedAt()));
        return m;
    }

    public static Map<String, Object> preferences(UserPreferencesEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled_channels", p.getEnabledChannels());
        m.put("escalation_order", p.getEscalationOrder());
        m.put("notification_methods", p.getNotificationMethods());
        m.put("quiet_hours_start", p.getQuietHoursStart());
        m.put("quiet_hours_end", p.getQuietHoursEnd());
        m.put("checkin_enabled", p.isCheckinEnabled());
        m.put("checkin_delay_minutes", p.getCheckinDelayMinutes());
        m.put("default_task_type", p.getDefaultTaskType());
        m.put("category_preferred_times", p.getCategoryPreferredTimes());
        m.put("suppressed_recommendation_types", p.getSuppressedRecommendationTypes());
        m.put("push_token", p.getPushToken());
        return m;
    }

    public static Map<String, Object> contact(EmergencyContactEntity c, String consentLink) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("phone", c.getPhone());
        m.put("consent_status", c.getConsentStatus());
        if (consentLink != null) m.put("consent_link", consentLink);
        return m;
    }

    public static Map<String, Object> profile(UserProfileEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("escalation_stats", p.getEscalationStats());
        m.put("alarm_patterns", p.getAlarmPatterns());
        m.put("task_completion_rates", p.getTaskCompletionRates());
        m.put("verification_stats", p.getVerificationStats());
        m.put("data_status", p.getDataStatus());
        m.put("skipped_rows", p.getSkippedRows());
        m.put("optimal_settings", p.getOptimalSettings());
        m.put("updated_at", iso(p.getUpdatedAt()));
        return m;
    }

    public static Map<String, Object> recommendation(RecommendationEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("type", r.getType());
        m.put("title", r.getTitle());
        m.put("rationale", r.getRationale());
        m.put("change", r.getChange());
        m.put("source", r.getSource());
        m.put("status", r.getStatus());
        m.put("created_at", iso(r.getCreatedAt()));
        return m;
    }

    public static Map<String, Object> notification(NotificationEntity n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("kind", n.getKind());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("data", n.getData());
        m.put("read", n.isRead());
        m.put("created_at", iso(n.getCreatedAt()));
        return m;
    }
}
