package com.liuqitech.accountingassistant.dto.analysis;

import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;

import java.util.List;

public record AnalysisPayloadV2(
        int schemaVersion,
        RawAnalysisPlan rawPlan,
        NormalizedAnalysisPlan normalizedPlan,
        List<AnalysisResult> results,
        AnalysisResponseStatus status,
        List<AnalysisWarning> warnings,
        List<AnalysisFollowUp> followUps) {

    public AnalysisPayloadV2 {
        results = results == null ? List.of() : List.copyOf(results);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        followUps = followUps == null ? List.of() : List.copyOf(followUps);
    }
}
