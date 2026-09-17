package com.liuqitech.accountingassistant.dto.analysis;

import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;

import java.util.List;

public record AnalysisExecutionOutcome(
        AnalysisResponseStatus status,
        List<AnalysisResult> results,
        List<AnalysisWarning> warnings) {

    public AnalysisExecutionOutcome {
        results = results == null ? List.of() : List.copyOf(results);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
