package com.liuqitech.accountingassistant.dto.analysis;

import com.liuqitech.accountingassistant.enums.AnalysisComparisonMode;
import com.liuqitech.accountingassistant.enums.AnalysisOperationKind;

import java.util.List;

public record NormalizedAnalysisPlan(
        AnalysisOperationKind operation,
        AnalysisComparisonMode comparisonMode,
        List<NormalizedAnalysisQuery> queries,
        String title) {

    public NormalizedAnalysisPlan {
        queries = queries == null ? List.of() : List.copyOf(queries);
    }
}
