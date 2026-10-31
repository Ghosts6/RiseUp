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
@Table(name = "escalation_logs")
public class EscalationLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "alarm_id")
    private Integer alarmId;

    @Column(nullable = false, length = 64)
    private String episode;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "trigger_event", nullable = false, length = 32)
    private String triggerEvent;

    @Column(length = 32)
    private String method;

    @Column(name = "decided_by", nullable = false, length = 64)
    private String decidedBy;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String reasoning = "";

    private Double confidence;

    @Column(name = "fallback_method", length = 32)
    private String fallbackMethod;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "CLOB")
    private Map<String, Object> inputs = new HashMap<>();

    @Column(nullable = false, length = 16)
    private String status = "sent";

    @Column(columnDefinition = "CLOB")
    private String error;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "response_time_seconds")
    private Double responseTimeSeconds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getAlarmId() { return alarmId; }
    public void setAlarmId(Integer alarmId) { this.alarmId = alarmId; }
    public String getEpisode() { return episode; }
    public void setEpisode(String episode) { this.episode = episode; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
    /** Alias used by older call sites. */
    public String getTrigger() { return triggerEvent; }
    public void setTrigger(String trigger) { this.triggerEvent = trigger; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getFallbackMethod() { return fallbackMethod; }
    public void setFallbackMethod(String fallbackMethod) { this.fallbackMethod = fallbackMethod; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public Map<String, Object> getInputs() { return inputs; }
    public void setInputs(Map<String, Object> inputs) { this.inputs = inputs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
    public Double getResponseTimeSeconds() { return responseTimeSeconds; }
    public void setResponseTimeSeconds(Double responseTimeSeconds) { this.responseTimeSeconds = responseTimeSeconds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
