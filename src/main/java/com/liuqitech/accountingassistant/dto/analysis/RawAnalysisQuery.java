package com.liuqitech.accountingassistant.dto.analysis;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class RawAnalysisQuery {
    private String id;
    @JsonPropertyDescription("AGGREGATE, BREAKDOWN, TREND, TRANSACTIONS")
    private String kind;
    @JsonPropertyDescription("SUM, COUNT, AVG")
    private String metric;
    @JsonPropertyDescription("INCOME or EXPENSE; null means all non-transfer")
    private String transactionType;
    @JsonPropertyDescription("only for BREAKDOWN: PARENT_CATEGORY, CHILD_CATEGORY, ACCOUNT, MERCHANT, TAG; otherwise null")
    private String dimension;
    @JsonPropertyDescription("only for TREND: DAY, WEEK, MONTH, QUARTER, YEAR; otherwise null")
    private String timeGrain;
    @JsonPropertyDescription("AMOUNT or DATE; otherwise null")
    private String sortField;
    @JsonPropertyDescription("ASC or DESC; otherwise null")
    private String sortDirection;
    private Integer limit;
    private String title;
    private RawAnalysisPeriod period;
    private RawAnalysisFilters filters;

    public RawAnalysisQuery() {}

    public String getId() { return id; }
    public void setId(String value) { this.id = value; }
    public String getKind() { return kind; }
    public void setKind(String value) { this.kind = value; }
    public String getMetric() { return metric; }
    public void setMetric(String value) { this.metric = value; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String value) { this.transactionType = value; }
    public String getDimension() { return dimension; }
    public void setDimension(String value) { this.dimension = value; }
    public String getTimeGrain() { return timeGrain; }
    public void setTimeGrain(String value) { this.timeGrain = value; }
    public String getSortField() { return sortField; }
    public void setSortField(String value) { this.sortField = value; }
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String value) { this.sortDirection = value; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer value) { this.limit = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public RawAnalysisPeriod getPeriod() { return period; }
    public void setPeriod(RawAnalysisPeriod value) { this.period = value; }
    public RawAnalysisFilters getFilters() { return filters; }
    public void setFilters(RawAnalysisFilters value) { this.filters = value; }
}
