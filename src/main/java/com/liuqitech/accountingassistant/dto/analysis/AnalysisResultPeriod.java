package com.liuqitech.accountingassistant.dto.analysis;

public record AnalysisResultPeriod(
        AnalysisDateRange current,
        AnalysisDateRange previous) {}
