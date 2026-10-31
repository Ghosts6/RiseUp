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
@Table(name = "recommendations")
public class RecommendationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 48)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String rationale;

    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "CLOB")
    private Map<String, Object> change = new HashMap<>();

    @Column(nullable = false, length = 32)
    private String source = "rules";

    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public Map<String, Object> getChange() { return change; }
    public void setChange(Map<String, Object> change) { this.change = change; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
