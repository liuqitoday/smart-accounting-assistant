package com.liuqitech.accountingassistant.dto;

import java.math.BigDecimal;

/**
 * 分类统计 DTO
 */
public class CategoryStatisticsDto {

    private Long categoryId;
    private String categoryName;
    private String parentCategoryName;
    private BigDecimal amount;
    private BigDecimal percentage;
    private Long transactionCount;

    public CategoryStatisticsDto() {
    }

    public CategoryStatisticsDto(Long categoryId, String categoryName, String parentCategoryName,
                                 BigDecimal amount, BigDecimal percentage, Long transactionCount) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.parentCategoryName = parentCategoryName;
        this.amount = amount;
        this.percentage = percentage;
        this.transactionCount = transactionCount;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getParentCategoryName() {
        return parentCategoryName;
    }

    public void setParentCategoryName(String parentCategoryName) {
        this.parentCategoryName = parentCategoryName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public Long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(Long transactionCount) {
        this.transactionCount = transactionCount;
    }
}
