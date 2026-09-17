package com.liuqitech.accountingassistant.service.analysis;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisComparisonRanges;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.enums.AnalysisComparisonMode;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;

import java.time.LocalDate;

public interface AnalysisPeriodResolver {
    AnalysisDateRange resolve(AnalysisPeriodSpec spec, LocalDate today);
    AnalysisComparisonRanges resolveCompare(AnalysisPeriodSpec spec,
                                             AnalysisComparisonMode mode,
                                             LocalDate today);
    int bucketCount(AnalysisDateRange range, AnalysisTimeGrain grain);
}
