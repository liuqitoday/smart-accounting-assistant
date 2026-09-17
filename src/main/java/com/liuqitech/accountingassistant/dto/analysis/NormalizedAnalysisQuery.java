package com.liuqitech.accountingassistant.dto.analysis;

import com.liuqitech.accountingassistant.enums.AnalysisDimension;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisQueryKind;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;
import com.liuqitech.accountingassistant.enums.TransactionType;

public record NormalizedAnalysisQuery(
        String id,
        AnalysisQueryKind kind,
        AnalysisMetric metric,
        TransactionType transactionType,
        AnalysisPeriodSpec periodSemantic,
        AnalysisDateRange period,
        AnalysisFilters filters,
        AnalysisDimension dimension,
        AnalysisTimeGrain timeGrain,
        AnalysisSortField sortField,
        AnalysisSortDirection sortDirection,
        int limit,
        String title) {}
