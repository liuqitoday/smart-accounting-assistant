package com.liuqitech.accountingassistant.dto.analysis;

import java.util.Objects;

public record AnalysisComparisonRanges(
        AnalysisDateRange current,
        AnalysisDateRange previous) {
    public AnalysisComparisonRanges {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(previous, "previous");
    }
}
