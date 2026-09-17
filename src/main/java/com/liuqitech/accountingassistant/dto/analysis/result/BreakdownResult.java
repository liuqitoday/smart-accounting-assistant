package com.liuqitech.accountingassistant.dto.analysis.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResult;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResultPeriod;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisWarning;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import com.liuqitech.accountingassistant.enums.AnalysisUnit;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.service.analysis.DerivedMetricCalculator;

import java.util.List;

public record BreakdownResult(String title, AnalysisMetric metric, AnalysisUnit unit,
        TransactionType transactionType, AnalysisResultPeriod period, AnalysisFilters filters,
        List<AnalysisWarning> warnings, List<DerivedMetricCalculator.BreakdownValues.Row> rows,
        boolean associationShare) implements AnalysisResult {
    public BreakdownResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    @Override
    @JsonProperty("kind")
    public AnalysisResultKind kind() { return AnalysisResultKind.BREAKDOWN; }
}
