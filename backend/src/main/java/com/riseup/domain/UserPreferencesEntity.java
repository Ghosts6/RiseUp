package com.riseup.domain;

import com.riseup.persistence.JsonListConverter;
import com.riseup.persistence.JsonMapConverter;
import jakarta.persistence.*;
import jakarta.persistence.Lob;
import java.util.*;

@Entity
@Table(name = "user_preferences")
public class UserPreferencesEntity {
    @Id
    @Column(name = "user_id")
    private Integer userId;
    @Convert(converter = JsonListConverter.class)
    @Column(name = "enabled_channels")
    private List<String> enabledChannels = new ArrayList<>(List.of("call", "sms", "alexa", "push"));
    @Convert(converter = JsonListConverter.class)
    @Column(name = "escalation_order")
    private List<String> escalationOrder = new ArrayList<>(List.of("call", "sms", "alexa", "push", "emergency_contact"));
    @Convert(converter = JsonListConverter.class)
    @Column(name = "notification_methods")
    private List<String> notificationMethods = new ArrayList<>(List.of("push", "sms"));
    @Column(name = "quiet_hours_start")
    private String quietHoursStart;
    @Column(name = "quiet_hours_end")
    private String quietHoursEnd;
    @Column(name = "checkin_enabled")
    private boolean checkinEnabled = true;
    @Column(name = "checkin_delay_minutes")
    private int checkinDelayMinutes = 7;
    @Column(name = "default_task_type")
    private String defaultTaskType = "math";
    @Column(name = "push_token")
    private String pushToken;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "category_preferred_times")
    private Map<String, Object> categoryPreferredTimes = new HashMap<>();
    @Convert(converter = JsonListConverter.class)
    @Column(name = "suppressed_recommendation_types")
    private List<String> suppressedRecommendationTypes = new ArrayList<>();
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "dismissed_recommendation_counts")
    private Map<String, Object> dismissedRecommendationCounts = new HashMap<>();

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public List<String> getEnabledChannels() { return enabledChannels; }
    public void setEnabledChannels(List<String> enabledChannels) { this.enabledChannels = enabledChannels; }
    public List<String> getEscalationOrder() { return escalationOrder; }
    public void setEscalationOrder(List<String> escalationOrder) { this.escalationOrder = escalationOrder; }
    public List<String> getNotificationMethods() { return notificationMethods; }
    public void setNotificationMethods(List<String> notificationMethods) { this.notificationMethods = notificationMethods; }
    public String getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(String quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public String getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(String quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
    public boolean isCheckinEnabled() { return checkinEnabled; }
    public void setCheckinEnabled(boolean checkinEnabled) { this.checkinEnabled = checkinEnabled; }
    public int getCheckinDelayMinutes() { return checkinDelayMinutes; }
    public void setCheckinDelayMinutes(int checkinDelayMinutes) { this.checkinDelayMinutes = checkinDelayMinutes; }
    public String getDefaultTaskType() { return defaultTaskType; }
    public void setDefaultTaskType(String defaultTaskType) { this.defaultTaskType = defaultTaskType; }
    public String getPushToken() { return pushToken; }
    public void setPushToken(String pushToken) { this.pushToken = pushToken; }
    public Map<String, Object> getCategoryPreferredTimes() { return categoryPreferredTimes; }
    public void setCategoryPreferredTimes(Map<String, Object> categoryPreferredTimes) { this.categoryPreferredTimes = categoryPreferredTimes; }
    public List<String> getSuppressedRecommendationTypes() { return suppressedRecommendationTypes; }
    public void setSuppressedRecommendationTypes(List<String> suppressedRecommendationTypes) { this.suppressedRecommendationTypes = suppressedRecommendationTypes; }
    public Map<String, Object> getDismissedRecommendationCounts() { return dismissedRecommendationCounts; }
    public void setDismissedRecommendationCounts(Map<String, Object> dismissedRecommendationCounts) { this.dismissedRecommendationCounts = dismissedRecommendationCounts; }
}
