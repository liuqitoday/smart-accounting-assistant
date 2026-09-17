package com.liuqitech.accountingassistant.dto;

import java.math.BigDecimal;

/**
 * 趋势数据 DTO
 */
public class TrendDataDto {

    private String month;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal balance;

    public TrendDataDto() {
        this.income = BigDecimal.ZERO;
        this.expense = BigDecimal.ZERO;
        this.balance = BigDecimal.ZERO;
    }

    public TrendDataDto(String month, BigDecimal income, BigDecimal expense) {
        this.month = month;
        this.income = income != null ? income : BigDecimal.ZERO;
        this.expense = expense != null ? expense : BigDecimal.ZERO;
        this.balance = this.income.subtract(this.expense);
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public void setIncome(BigDecimal income) {
        this.income = income;
    }

    public BigDecimal getExpense() {
        return expense;
    }

    public void setExpense(BigDecimal expense) {
        this.expense = expense;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
