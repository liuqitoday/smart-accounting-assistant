package com.liuqitech.accountingassistant.entity;

import com.liuqitech.accountingassistant.enums.LedgerRole;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 账本成员实体：记录某用户在某账本中的角色。
 *
 * <p>账本所有者自身也有一行，角色为 {@link LedgerRole#OWNER}。
 * 每个 (账本, 用户) 组合至多一行。</p>
 */
@Entity
@Table(
        name = "ledger_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_ledger_user",
                columnNames = {"ledger_id", "username"}
        )
)
public class LedgerMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ledger_id", nullable = false)
    private Long ledgerId;

    @Column(nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerRole role;

    /**
     * 该成员在本账本的默认账户（个人偏好，账本内各成员互不影响）；null 表示未设置。
     * 新建交易时前端据此自动选中账户。
     */
    @Column(name = "default_account_id")
    private Long defaultAccountId;

    /** 加入时间 */
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public LedgerMember() {}

    public LedgerMember(Long ledgerId, String username, LedgerRole role) {
        this.ledgerId = ledgerId;
        this.username = username;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLedgerId() {
        return ledgerId;
    }

    public void setLedgerId(Long ledgerId) {
        this.ledgerId = ledgerId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LedgerRole getRole() {
        return role;
    }

    public void setRole(LedgerRole role) {
        this.role = role;
    }

    public Long getDefaultAccountId() {
        return defaultAccountId;
    }

    public void setDefaultAccountId(Long defaultAccountId) {
        this.defaultAccountId = defaultAccountId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "LedgerMember{" +
                "id=" + id +
                ", ledgerId=" + ledgerId +
                ", username='" + username + '\'' +
                ", role=" + role +
                '}';
    }
}
