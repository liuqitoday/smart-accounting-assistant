package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户 DTO。
 *
 * <p>{@link #currentBalance} 为「期初余额 + 关联交易收支净额」的实时计算值，不持久化。</p>
 *
 * <p>{@link #isDefault} 表示「当前登录用户在当前账本的默认账户」，是个人偏好而非账户固有属性，
 * 因此同一账本对不同用户返回的该字段可能不同。getter 命名为 {@code isDefault()}，
 * 序列化后 JSON 字段名为 {@code default}，与 {@code LedgerDto} 的既定约定一致。</p>
 */
public class AccountDto {

    private Long id;
    private String name;
    private AccountType type;
    private BigDecimal initialBalance;
    /** 当前余额（计算值）：期初 + Σ收入 − Σ支出 */
    private BigDecimal currentBalance;
    private String icon;
    private String color;
    private boolean active;
    /** 是否为当前用户在当前账本的默认账户 */
    private boolean isDefault;
    /** 关联本账户的交易笔数 */
    private long transactionCount;
    private LocalDateTime createdAt;

    public AccountDto() {}

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

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
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

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(long transactionCount) {
        this.transactionCount = transactionCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
