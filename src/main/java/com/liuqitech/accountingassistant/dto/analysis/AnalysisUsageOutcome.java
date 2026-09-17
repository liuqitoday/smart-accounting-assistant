package com.liuqitech.accountingassistant.dto.analysis;

import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;

import java.time.Duration;
import java.util.Set;

public record AnalysisUsageOutcome(
        AnalysisResponseStatus status,
        Duration duration,
        int queryCount,
        int resultRowCount) {
    public static AnalysisUsageOutcome successful(AnalysisResponseStatus status,
                                                   Duration duration,
                                                   int queryCount,
                                                   int resultRowCount) {
        if (!Set.of(AnalysisResponseStatus.OK, AnalysisResponseStatus.NO_DATA,
                    AnalysisResponseStatus.PARTIAL_RESULT).contains(status)) {
            throw new IllegalArgumentException("status 不是 successful 类别");
        }
        return new AnalysisUsageOutcome(status, duration, queryCount, resultRowCount);
    }
    public static AnalysisUsageOutcome success(Duration duration, int queryCount,
                                                int resultRowCount) {
        return successful(AnalysisResponseStatus.OK, duration, queryCount, resultRowCount);
    }
    public static AnalysisUsageOutcome noData(Duration duration, int queryCount) {
        return successful(AnalysisResponseStatus.NO_DATA, duration, queryCount, 0);
    }
    public static AnalysisUsageOutcome partial(Duration duration, int queryCount,
                                                int resultRowCount) {
        return successful(AnalysisResponseStatus.PARTIAL_RESULT, duration,
            queryCount, resultRowCount);
    }
    public static AnalysisUsageOutcome clarification(Duration duration) {
        return new AnalysisUsageOutcome(AnalysisResponseStatus.CLARIFICATION_REQUIRED,
            duration, 0, 0);
    }
    public static AnalysisUsageOutcome failed(AnalysisResponseStatus status,
                                               Duration duration) {
        if (!Set.of(AnalysisResponseStatus.INVALID_PLAN,
                    AnalysisResponseStatus.AI_UNAVAILABLE,
                    AnalysisResponseStatus.QUERY_FAILED).contains(status)) {
            throw new IllegalArgumentException("status 不是 failed 类别");
        }
        return new AnalysisUsageOutcome(status, duration, 0, 0);
    }
}
