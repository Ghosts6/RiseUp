package com.riseup.domain;

import com.riseup.persistence.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "alarms")
public class AlarmEntity {
    public static final Set<String> ACTIVE_STATES =
            Set.of("ringing", "snoozed", "escalating", "awaiting_checkin");
    public static final Set<String> TASK_TYPES =
            Set.of("math", "trivia", "object_detection", "custom");
    public static final Set<String> VIBRATION_LEVELS = Set.of("low", "medium", "high");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 5)
    private String time;

    @Column(nullable = false, length = 120)
    private String label = "Alarm";

    @Column(nullable = false, length = 64)
    private String sound = "classic";

    @Column(nullable = false, length = 16)
    private String vibration = "medium";

    @Column(name = "repeat_pattern", nullable = false, length = 64)
    private String repeatPattern = "once";

    @Column(name = "snooze_enabled", nullable = false)
    private boolean snoozeEnabled;

    @Column(name = "verification_task_type", nullable = false, length = 32)
    private String verificationTaskType = "math";

    @Column(name = "custom_task_text", columnDefinition = "CLOB")
    private String customTaskText;

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false, length = 32)
    private String status = "idle";

    @Column(name = "next_trigger_at")
    private LocalDateTime nextTriggerAt;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "snooze_count", nullable = false)
    private int snoozeCount;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "camera_failures", nullable = false)
    private int cameraFailures;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "current_task", columnDefinition = "CLOB")
    private Map<String, Object> currentTask;

    @Column(name = "escalation_episode", length = 64)
    private String escalationEpisode;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isRunning() {
        return ACTIVE_STATES.contains(status);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSound() { return sound; }
    public void setSound(String sound) { this.sound = sound; }
    public String getVibration() { return vibration; }
    public void setVibration(String vibration) { this.vibration = vibration; }
    public String getRepeatPattern() { return repeatPattern; }
    public void setRepeatPattern(String repeatPattern) { this.repeatPattern = repeatPattern; }
    public boolean isSnoozeEnabled() { return snoozeEnabled; }
    public void setSnoozeEnabled(boolean snoozeEnabled) { this.snoozeEnabled = snoozeEnabled; }
    public String getVerificationTaskType() { return verificationTaskType; }
    public void setVerificationTaskType(String verificationTaskType) { this.verificationTaskType = verificationTaskType; }
    public String getCustomTaskText() { return customTaskText; }
    public void setCustomTaskText(String customTaskText) { this.customTaskText = customTaskText; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getNextTriggerAt() { return nextTriggerAt; }
    public void setNextTriggerAt(LocalDateTime nextTriggerAt) { this.nextTriggerAt = nextTriggerAt; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
    public int getSnoozeCount() { return snoozeCount; }
    public void setSnoozeCount(int snoozeCount) { this.snoozeCount = snoozeCount; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public int getCameraFailures() { return cameraFailures; }
    public void setCameraFailures(int cameraFailures) { this.cameraFailures = cameraFailures; }
    public Map<String, Object> getCurrentTask() { return currentTask; }
    public void setCurrentTask(Map<String, Object> currentTask) { this.currentTask = currentTask; }
    public String getEscalationEpisode() { return escalationEpisode; }
    public void setEscalationEpisode(String escalationEpisode) { this.escalationEpisode = escalationEpisode; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
