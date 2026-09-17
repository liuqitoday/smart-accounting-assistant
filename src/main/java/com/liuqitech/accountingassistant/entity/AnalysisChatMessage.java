package com.liuqitech.accountingassistant.entity;

import jakarta.persistence.*;

import com.liuqitech.accountingassistant.util.AppClock;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_chat_messages")
public class AnalysisChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ledger_id", nullable = false)
    private Long ledgerId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(nullable = false, length = 20)
    private String role;  // USER | ASSISTANT

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String payload;  // JSON: 查询计划 + 结果数据

    @Column(nullable = false, length = 20)
    private String status;  // OK | FAILED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 用 AppClock（Asia/Shanghai）而非 @CreationTimestamp（JVM 默认时区）写入：
     * createdAt 是每日限流 countUserMessagesToday 的比较基准，必须与读侧的 AppClock 日界同区，
     * 否则公网 UTC 服务器上日界会错 8 小时。
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = AppClock.now();
        }
    }

    public AnalysisChatMessage() {}

    public AnalysisChatMessage(Long ledgerId, String userId, String role, String content, String payload, String status) {
        this.ledgerId = ledgerId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.payload = payload;
        this.status = status;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLedgerId() { return ledgerId; }
    public void setLedgerId(Long ledgerId) { this.ledgerId = ledgerId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
