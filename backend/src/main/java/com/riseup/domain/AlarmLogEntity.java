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
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "alarm_logs")
public class AlarmLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "alarm_id")
    private Integer alarmId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 32)
    private String event;

    @Column(name = "task_type", length = 32)
    private String taskType;

    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "CLOB")
    private Map<String, Object> details = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getAlarmId() { return alarmId; }
    public void setAlarmId(Integer alarmId) { this.alarmId = alarmId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
