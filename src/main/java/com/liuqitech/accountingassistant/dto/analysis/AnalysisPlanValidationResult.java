package com.liuqitech.accountingassistant.dto.analysis;

import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;

import java.util.List;

public record AnalysisPlanValidationResult(
        AnalysisResponseStatus status,
        NormalizedAnalysisPlan normalizedPlan,
        List<String> errors) {

    public AnalysisPlanValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static AnalysisPlanValidationResult valid(NormalizedAnalysisPlan plan) {
        return new AnalysisPlanValidationResult(AnalysisResponseStatus.OK, plan, List.of());
    }

    public static AnalysisPlanValidationResult invalid(List<String> errors) {
        return new AnalysisPlanValidationResult(AnalysisResponseStatus.INVALID_PLAN,
            null, List.copyOf(errors));
    }

    public static AnalysisPlanValidationResult clarification(List<String> errors) {
        return new AnalysisPlanValidationResult(AnalysisResponseStatus.CLARIFICATION_REQUIRED,
            null, List.copyOf(errors));
    }
}
