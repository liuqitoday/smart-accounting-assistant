package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.*;
import java.math.BigDecimal;

public class AnalysisQuery {
    private AnalysisQueryType queryType;
    private AnalysisMetric metric;
    private String txType;  // "EXPENSE" | "INCOME" | "ALL"
    private String dateStart;
    private String dateEnd;
    private AnalysisGroupBy groupBy;
    private AnalysisOrderBy orderBy;
    private Integer limit;
    private String title;  // 卡片标题，AI 起

    // Filters
    private Long categoryId;
    private Long accountId;
    private Long tagId;
    private String merchant;
    private String keyword;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    // Getters and setters
    public AnalysisQueryType getQueryType() { return queryType; }
    public void setQueryType(AnalysisQueryType queryType) { this.queryType = queryType; }

    public AnalysisMetric getMetric() { return metric; }
    public void setMetric(AnalysisMetric metric) { this.metric = metric; }

    public String getTxType() { return txType; }
    public void setTxType(String txType) { this.txType = txType; }

    public String getDateStart() { return dateStart; }
    public void setDateStart(String dateStart) { this.dateStart = dateStart; }

    public String getDateEnd() { return dateEnd; }
    public void setDateEnd(String dateEnd) { this.dateEnd = dateEnd; }

    public AnalysisGroupBy getGroupBy() { return groupBy; }
    public void setGroupBy(AnalysisGroupBy groupBy) { this.groupBy = groupBy; }

    public AnalysisOrderBy getOrderBy() { return orderBy; }
    public void setOrderBy(AnalysisOrderBy orderBy) { this.orderBy = orderBy; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }

    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
}
