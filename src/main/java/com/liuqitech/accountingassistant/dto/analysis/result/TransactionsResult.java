package com.liuqitech.accountingassistant.dto.analysis.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResult;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResultPeriod;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTransactionFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisWarning;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import com.liuqitech.accountingassistant.enums.AnalysisUnit;
import com.liuqitech.accountingassistant.enums.TransactionType;

import java.util.List;

public record TransactionsResult(String title, AnalysisMetric metric, AnalysisUnit unit,
        TransactionType transactionType, AnalysisResultPeriod period, AnalysisFilters filters,
        List<AnalysisWarning> warnings, Values values,
        List<AnalysisTransactionFact> transactions) implements AnalysisResult {
    public TransactionsResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }

    public record Values(long totalCount, int displayedCount, boolean detailsOmitted) {}

    @Override
    @JsonProperty("kind")
    public AnalysisResultKind kind() { return AnalysisResultKind.TRANSACTIONS; }
}
