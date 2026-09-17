package com.liuqitech.accountingassistant.service.analysis;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisExecutionOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFollowUp;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResult;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisPlan;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnalysisFollowUpService {

    public List<AnalysisFollowUp> suggest(NormalizedAnalysisPlan plan,
                                          AnalysisExecutionOutcome outcome) {
        AnalysisResultKind kind = primaryKind(plan, outcome);
        return switch (kind) {
            case AGGREGATE -> List.of(
                followUp("按分类拆分"),
                followUp("和上月比"));
            case PERIOD_COMPARE -> List.of(
                followUp("查看明细"),
                followUp("按分类拆分"));
            case TREND -> List.of(
                followUp("查看明细"),
                followUp("换成去年"));
            case TRANSACTIONS -> List.of(
                followUp("只看超过 500 元"),
                followUp("按账户拆分"));
            case NET_CASH_FLOW -> List.of(
                followUp("查看明细"),
                followUp("按分类拆分"));
            case AVERAGE_BY_PERIOD -> List.of(
                followUp("查看明细"),
                followUp("按月趋势"));
            case BREAKDOWN -> List.of(
                followUp("按账户拆分"),
                followUp("查看明细"));
        };
    }

    private static AnalysisFollowUp followUp(String question) {
        return new AnalysisFollowUp(question, question);
    }

    private static AnalysisResultKind primaryKind(NormalizedAnalysisPlan plan,
                                                  AnalysisExecutionOutcome outcome) {
        if (outcome != null && outcome.results() != null && !outcome.results().isEmpty()) {
            AnalysisResult first = outcome.results().get(0);
            if (first != null && first.kind() != null) {
                return first.kind();
            }
        }
        if (plan != null && plan.operation() != null) {
            return switch (plan.operation()) {
                case AGGREGATE -> AnalysisResultKind.AGGREGATE;
                case PERIOD_COMPARE -> AnalysisResultKind.PERIOD_COMPARE;
                case NET_CASH_FLOW -> AnalysisResultKind.NET_CASH_FLOW;
                case BREAKDOWN -> AnalysisResultKind.BREAKDOWN;
                case TREND -> AnalysisResultKind.TREND;
                case AVERAGE_BY_PERIOD -> AnalysisResultKind.AVERAGE_BY_PERIOD;
                case TRANSACTIONS -> AnalysisResultKind.TRANSACTIONS;
                case COMPOSITE -> AnalysisResultKind.AGGREGATE;
            };
        }
        return AnalysisResultKind.AGGREGATE;
    }
}
