package com.liuqitech.accountingassistant.dto.analysis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RawAnalysisFilters {
    private Long categoryId;
    private Long accountId;
    private List<Long> tagIds = new ArrayList<>();
    private String merchant;
    private String keyword;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    public RawAnalysisFilters() {}

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { this.categoryId = value; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long value) { this.accountId = value; }
    public List<Long> getTagIds() { return tagIds; }
    public void setTagIds(List<Long> value) { this.tagIds = value; }
    public String getMerchant() { return merchant; }
    public void setMerchant(String value) { this.merchant = value; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String value) { this.keyword = value; }
    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal value) { this.minAmount = value; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal value) { this.maxAmount = value; }
}
