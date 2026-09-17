package com.liuqitech.accountingassistant.entity;

import com.liuqitech.accountingassistant.util.AppClock;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    /** 当前/默认账本（跨设备记住所选账本，作为 X-Ledger-Id 缺省回退） */
    @Column(name = "default_ledger_id")
    private Long defaultLedgerId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = AppClock.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getDefaultLedgerId() {
        return defaultLedgerId;
    }

    public void setDefaultLedgerId(Long defaultLedgerId) {
        this.defaultLedgerId = defaultLedgerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
