package com.liuqitech.accountingassistant.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import com.liuqitech.accountingassistant.enums.AnalysisUnit;
import com.liuqitech.accountingassistant.enums.TransactionType;

import java.util.List;

public interface AnalysisResult {
    @JsonProperty("kind")
    AnalysisResultKind kind();
    String title();
    AnalysisMetric metric();
    AnalysisUnit unit();
    TransactionType transactionType();
    AnalysisResultPeriod period();
    AnalysisFilters filters();
    List<AnalysisWarning> warnings();
}
