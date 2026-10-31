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
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "reminder_logs")
public class ReminderLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "reminder_id")
    private Integer reminderId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 32)
    private String event;

    private Integer level;

    @Column(length = 16)
    private String channel;

    @Column(length = 16)
    private String category;

    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "CLOB")
    private Map<String, Object> details = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getReminderId() { return reminderId; }
    public void setReminderId(Integer reminderId) { this.reminderId = reminderId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
