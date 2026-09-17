package com.liuqitech.accountingassistant.dto;

import java.util.List;

public class AnalysisQueryPlan {
    private boolean understood;  // AI 是否理解问题
    private String clarification;  // understood=false 时给用户的说明/反问
    private List<AnalysisQuery> queries;  // 1~3 个查询

    public boolean isUnderstood() { return understood; }
    public void setUnderstood(boolean understood) { this.understood = understood; }

    public String getClarification() { return clarification; }
    public void setClarification(String clarification) { this.clarification = clarification; }

    public List<AnalysisQuery> getQueries() { return queries; }
    public void setQueries(List<AnalysisQuery> queries) { this.queries = queries; }
}
