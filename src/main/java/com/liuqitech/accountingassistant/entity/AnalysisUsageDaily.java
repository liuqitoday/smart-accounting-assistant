package com.liuqitech.accountingassistant.entity;

import com.liuqitech.accountingassistant.util.LocalDateStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(name = "analysis_usage_daily", uniqueConstraints = {
    @UniqueConstraint(name = "uk_analysis_usage_daily_user_date", columnNames = {"user_id", "business_date"})
})
public class AnalysisUsageDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Convert(converter = LocalDateStringConverter.class)
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "successful_count", nullable = false)
    private int successfulCount;

    @Column(name = "clarification_count", nullable = false)
    private int clarificationCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    public AnalysisUsageDaily() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public int getSuccessfulCount() { return successfulCount; }
    public void setSuccessfulCount(int successfulCount) { this.successfulCount = successfulCount; }

    public int getClarificationCount() { return clarificationCount; }
    public void setClarificationCount(int clarificationCount) { this.clarificationCount = clarificationCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
}
