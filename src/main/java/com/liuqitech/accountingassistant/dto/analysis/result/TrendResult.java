package com.liuqitech.accountingassistant.dto.analysis.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResult;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResultPeriod;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisWarning;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import com.liuqitech.accountingassistant.enums.AnalysisUnit;
import com.liuqitech.accountingassistant.enums.TransactionType;

import java.util.List;

public record TrendResult(String title, AnalysisMetric metric, AnalysisUnit unit,
        TransactionType transactionType, AnalysisResultPeriod period, AnalysisFilters filters,
        List<AnalysisWarning> warnings, List<AnalysisTrendFact.Point> points) implements AnalysisResult {
    public TrendResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        points = points == null ? List.of() : List.copyOf(points);
    }

    @Override
    @JsonProperty("kind")
    public AnalysisResultKind kind() { return AnalysisResultKind.TREND; }
}
