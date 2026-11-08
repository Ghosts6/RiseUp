package com.riseup.domain;

import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "escalation_stats", nullable = false, columnDefinition = "CLOB")
    private Map<String, Object> escalationStats = new HashMap<>();

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "alarm_patterns", nullable = false, columnDefinition = "CLOB")
    private Map<String, Object> alarmPatterns = new HashMap<>();

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "task_completion_rates", nullable = false, columnDefinition = "CLOB")
    private Map<String, Object> taskCompletionRates = new HashMap<>();

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "verification_stats", nullable = false, columnDefinition = "CLOB")
    private Map<String, Object> verificationStats = new HashMap<>();

    @Column(name = "data_status", nullable = false, length = 32)
    private String dataStatus = "insufficient";

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @SuppressWarnings("unchecked")
    public void recordEscalationOutcome(String method, boolean success, Double responseSeconds) {
        if (method == null || method.isBlank()) return;
        Map<String, Object> entry =
                (Map<String, Object>)
                        escalationStats.computeIfAbsent(method, k -> new LinkedHashMap<>());
        int attempts = ((Number) entry.getOrDefault("attempts", 0)).intValue() + 1;
        int successes =
                ((Number) entry.getOrDefault("successes", 0)).intValue() + (success ? 1 : 0);
        entry.put("attempts", attempts);
        entry.put("successes", successes);
        entry.put("success_rate", attempts == 0 ? 0.0 : (double) successes / attempts);
        if (responseSeconds != null) {
            entry.put("last_response_seconds", responseSeconds);
        }
        updatedAt = LocalDateTime.now();
    }

    public Map<String, Object> getOptimalSettings() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("preferred_escalation", bestMethod());
        out.put("data_status", dataStatus);
        return out;
    }

    @SuppressWarnings("unchecked")
    private String bestMethod() {
        String best = null;
        double bestRate = -1;
        for (Map.Entry<String, Object> e : escalationStats.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> m)) continue;
            Object rate = m.get("success_rate");
            Object attempts = m.get("attempts");
            if (rate instanceof Number r && attempts instanceof Number a && a.intValue() > 0) {
                if (r.doubleValue() > bestRate) {
                    bestRate = r.doubleValue();
                    best = e.getKey();
                }
            }
        }
        return best;
    }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Map<String, Object> getEscalationStats() { return escalationStats; }
    public void setEscalationStats(Map<String, Object> escalationStats) { this.escalationStats = escalationStats; }
    public Map<String, Object> getAlarmPatterns() { return alarmPatterns; }
    public void setAlarmPatterns(Map<String, Object> alarmPatterns) { this.alarmPatterns = alarmPatterns; }
    public Map<String, Object> getTaskCompletionRates() { return taskCompletionRates; }
    public void setTaskCompletionRates(Map<String, Object> taskCompletionRates) { this.taskCompletionRates = taskCompletionRates; }
    public Map<String, Object> getVerificationStats() { return verificationStats; }
    public void setVerificationStats(Map<String, Object> verificationStats) { this.verificationStats = verificationStats; }
    public String getDataStatus() { return dataStatus; }
    public void setDataStatus(String dataStatus) { this.dataStatus = dataStatus; }
    public int getSkippedRows() { return skippedRows; }
    public void setSkippedRows(int skippedRows) { this.skippedRows = skippedRows; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
