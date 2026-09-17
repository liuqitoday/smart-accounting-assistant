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

import java.math.BigDecimal;
import java.util.List;

public record PeriodCompareResult(String title, AnalysisMetric metric, AnalysisUnit unit,
        TransactionType transactionType, AnalysisResultPeriod period, AnalysisFilters filters,
        List<AnalysisWarning> warnings, Values values) implements AnalysisResult {
    public PeriodCompareResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record Values(BigDecimal current, BigDecimal previous,
                         BigDecimal difference, BigDecimal changeRate) {}

    @Override
    @JsonProperty("kind")
    public AnalysisResultKind kind() { return AnalysisResultKind.PERIOD_COMPARE; }
}
