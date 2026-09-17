package com.liuqitech.accountingassistant.entity;

import com.liuqitech.accountingassistant.util.AppClock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_usage_events")
public class AnalysisUsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "query_count", nullable = false)
    private int queryCount;

    @Column(name = "result_row_count", nullable = false)
    private int resultRowCount;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = AppClock.now();
        }
    }

    public AnalysisUsageEvent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public int getQueryCount() { return queryCount; }
    public void setQueryCount(int queryCount) { this.queryCount = queryCount; }

    public int getResultRowCount() { return resultRowCount; }
    public void setResultRowCount(int resultRowCount) { this.resultRowCount = resultRowCount; }
}
