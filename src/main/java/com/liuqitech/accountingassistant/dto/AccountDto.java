package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户 DTO。
 *
 * <p>{@link #currentBalance} 为「期初余额 + 关联交易收支净额」的实时计算值，不持久化。</p>
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
