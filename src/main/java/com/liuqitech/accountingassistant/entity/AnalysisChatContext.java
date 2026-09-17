package com.liuqitech.accountingassistant.entity;

import com.liuqitech.accountingassistant.util.AppClock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_chat_contexts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_analysis_context_ledger_user", columnNames = {"ledger_id", "user_id"})
})
public class AnalysisChatContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ledger_id", nullable = false)
    private Long ledgerId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = AppClock.now();
    }

    public AnalysisChatContext() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLedgerId() { return ledgerId; }
    public void setLedgerId(Long ledgerId) { this.ledgerId = ledgerId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
