package com.liuqitech.accountingassistant.entity;

import com.liuqitech.accountingassistant.enums.AccountType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产账户实体（银行卡、支付宝、基金等）。
 *
 * <p>账户归属于账本（{@link #ledgerId}），成员共享。账户只持久化「期初余额」
 * （{@link #initialBalance}）；当前余额由「期初 + 关联交易的收支净额」实时计算，
 * <b>不在库中存储可变余额</b>，以避免交易多写入路径下的账实漂移。</p>
 *
 * <p>沿用项目「按 id 引用 + repository 查询」风格，不建立到交易的 JPA 集合关系。
 * 注意：Hibernate 的 SQLite 方言不会创建 {@code @UniqueConstraint}，账本内账户名唯一性
 * 由 service 查重 + 启动时显式创建的唯一索引共同保证。</p>
 */
@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_name_ledger", columnNames = {"name", "ledger_id"})
})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    /** 期初余额（开账金额）。当前余额 = 期初 + Σ收入 − Σ支出。 */
    @Column(name = "initial_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal initialBalance = BigDecimal.ZERO;

    /** emoji 或图标 key，用于前端区分账户 */
    @Column(length = 50)
    private String icon;

    /** hex 颜色，复用标签/账本的颜色约定 */
    @Column(length = 20)
    private String color;

    /** 是否在用；false 表示停用归档（保留历史、隐藏于新建关联选择器） */
    @Column(nullable = false)
    private boolean active = true;

    /** 所属账本（数据隔离维度，成员共享） */
    @Column(name = "ledger_id")
    private Long ledgerId;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Account() {}

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

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getLedgerId() {
        return ledgerId;
    }

    public void setLedgerId(Long ledgerId) {
        this.ledgerId = ledgerId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
        return "Account{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", initialBalance=" + initialBalance +
                ", active=" + active +
                ", ledgerId=" + ledgerId +
                '}';
    }
}
