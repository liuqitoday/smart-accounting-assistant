package com.liuqitech.accountingassistant.dto.analysis;

import com.liuqitech.accountingassistant.enums.AnalysisDimension;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import com.liuqitech.accountingassistant.enums.TransactionType;

import java.util.Map;

public record AnalysisContextSummary(
        AnalysisPeriodSpec periodSemantic,
        AnalysisDateRange resolvedPeriod,
        TransactionType transactionType,
        AnalysisMetric metric,
        AnalysisDimension dimension,
        AnalysisFilters filters,
        AnalysisResultKind resultKind,
        Map<String, String> resultSummary) {

    public AnalysisContextSummary {
        filters = filters == null ? AnalysisFilters.empty() : filters;
        resultSummary = resultSummary == null ? Map.of() : Map.copyOf(resultSummary);
    }
}
