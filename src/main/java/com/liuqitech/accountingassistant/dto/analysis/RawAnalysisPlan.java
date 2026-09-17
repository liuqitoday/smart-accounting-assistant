package com.liuqitech.accountingassistant.dto.analysis;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;

public class RawAnalysisPlan {
    @JsonPropertyDescription("AGGREGATE, PERIOD_COMPARE, NET_CASH_FLOW, BREAKDOWN, TREND, AVERAGE_BY_PERIOD, TRANSACTIONS, COMPOSITE")
    private String operation;
    @JsonPropertyDescription("only for PERIOD_COMPARE: PREVIOUS_PERIOD or SAME_PERIOD_LAST_YEAR; otherwise null")
    private String comparisonMode;
    private List<RawAnalysisQuery> queries = new ArrayList<>();
    private String title;

    public RawAnalysisPlan() {}

    public String getOperation() { return operation; }
    public void setOperation(String value) { this.operation = value; }
    public String getComparisonMode() { return comparisonMode; }
    public void setComparisonMode(String value) { this.comparisonMode = value; }
    public List<RawAnalysisQuery> getQueries() { return queries; }
    public void setQueries(List<RawAnalysisQuery> value) { this.queries = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
}
