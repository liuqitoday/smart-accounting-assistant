package com.liuqitech.accountingassistant.dto;

import java.math.BigDecimal;

/**
 * 统计摘要 DTO
 */
public class StatisticsSummaryDto {

    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal balance;
    private Long transactionCount;
    private String periodStart;
    private String periodEnd;
    /** 上一同期收入（用于环比） */
    private BigDecimal prevTotalIncome;
    /** 上一同期支出 */
    private BigDecimal prevTotalExpense;
    /** 收入环比百分比（正=增、负=减；上一期为 0 时为 null） */
    private Double incomeChangePct;
    /** 支出环比百分比 */
    private Double expenseChangePct;

    public StatisticsSummaryDto() {
        this.totalIncome = BigDecimal.ZERO;
        this.totalExpense = BigDecimal.ZERO;
        this.balance = BigDecimal.ZERO;
        this.transactionCount = 0L;
        this.prevTotalIncome = BigDecimal.ZERO;
        this.prevTotalExpense = BigDecimal.ZERO;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(BigDecimal totalExpense) {
        this.totalExpense = totalExpense;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(Long transactionCount) {
        this.transactionCount = transactionCount;
    }

    public String getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(String periodStart) {
        this.periodStart = periodStart;
    }

    public String getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(String periodEnd) {
        this.periodEnd = periodEnd;
    }

    public BigDecimal getPrevTotalIncome() {
        return prevTotalIncome;
    }

    public void setPrevTotalIncome(BigDecimal prevTotalIncome) {
        this.prevTotalIncome = prevTotalIncome;
    }

    public BigDecimal getPrevTotalExpense() {
        return prevTotalExpense;
    }

    public void setPrevTotalExpense(BigDecimal prevTotalExpense) {
        this.prevTotalExpense = prevTotalExpense;
    }

    public Double getIncomeChangePct() {
        return incomeChangePct;
    }

    public void setIncomeChangePct(Double incomeChangePct) {
        this.incomeChangePct = incomeChangePct;
    }

    public Double getExpenseChangePct() {
        return expenseChangePct;
    }

    public void setExpenseChangePct(Double expenseChangePct) {
        this.expenseChangePct = expenseChangePct;
    }
}
