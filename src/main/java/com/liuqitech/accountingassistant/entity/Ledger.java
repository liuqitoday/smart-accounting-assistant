package com.liuqitech.accountingassistant.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 账本实体。
 *
 * <p>账本是数据隔离的核心维度：交易、标签、AI 纠正记忆都归属于某个账本。
 * 账本由某个用户创建（{@link #ownerUsername}），可邀请其他用户作为成员共同维护，
 * 成员与角色记录在 {@link LedgerMember}。</p>
 *
 * <p>沿用项目「按 id 引用 + repository 查询」的风格，不在此建立到成员/交易的 JPA 集合关系，避免懒加载发散。</p>
 */
@Entity
@Table(name = "ledgers")
public class Ledger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** emoji 或图标 key，用于前端区分账本 */
    @Column(length = 50)
    private String icon;

    /** hex 颜色，复用标签的颜色约定 */
    @Column(length = 20)
    private String color;

    /** 所有者用户名（冗余存储，便于「我是否所有者」判断与所有权转让） */
    @Column(name = "owner_username", nullable = false, length = 50)
    private String ownerUsername;

    /** 是否为该用户的默认账本（迁移时自动创建的账本） */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Ledger() {}

    public Ledger(String name, String ownerUsername) {
        this.name = name;
        this.ownerUsername = ownerUsername;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Ledger{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", ownerUsername='" + ownerUsername + '\'' +
                ", isDefault=" + isDefault +
                '}';
    }
}
