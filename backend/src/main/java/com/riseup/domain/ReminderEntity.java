package com.riseup.domain;

import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "reminders")
public class ReminderEntity {
    public static final Set<String> OPEN = Set.of("scheduled", "notifying", "queued");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "CLOB")
    private String notes;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(nullable = false, length = 16)
    private String priority = "medium";

    @Column(nullable = false, length = 16)
    private String category = "other";

    @Column(name = "repeat_pattern", nullable = false, length = 16)
    private String repeatPattern = "none";

    @Column(name = "series_id", length = 64)
    private String seriesId;

    @Column(name = "series_time", length = 5)
    private String seriesTime;

    @Column(name = "notification_type", nullable = false, length = 16)
    private String notificationType = "push";

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "extra_times", nullable = false, columnDefinition = "CLOB")
    private List<Object> extraTimes = new ArrayList<>();

    @Column(nullable = false, length = 16)
    private String status = "scheduled";

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "first_notified_at")
    private LocalDateTime firstNotifiedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "snooze_count", nullable = false)
    private int snoozeCount;

    @Column(nullable = false, length = 16)
    private String source = "form";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isOpen() { return OPEN.contains(status); }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getRepeatPattern() { return repeatPattern; }
    public void setRepeatPattern(String repeatPattern) { this.repeatPattern = repeatPattern; }
    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }
    public String getSeriesTime() { return seriesTime; }
    public void setSeriesTime(String seriesTime) { this.seriesTime = seriesTime; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public List<Object> getExtraTimes() { return extraTimes; }
    public void setExtraTimes(List<Object> extraTimes) { this.extraTimes = extraTimes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(int escalationLevel) { this.escalationLevel = escalationLevel; }
    public LocalDateTime getFirstNotifiedAt() { return firstNotifiedAt; }
    public void setFirstNotifiedAt(LocalDateTime firstNotifiedAt) { this.firstNotifiedAt = firstNotifiedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public int getSnoozeCount() { return snoozeCount; }
    public void setSnoozeCount(int snoozeCount) { this.snoozeCount = snoozeCount; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
