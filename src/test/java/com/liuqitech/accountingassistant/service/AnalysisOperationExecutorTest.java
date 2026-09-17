package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisAggregateFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisBreakdownFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisExecutionOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTransactionFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisWarning;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.result.AggregateResult;
import com.liuqitech.accountingassistant.dto.analysis.result.AverageByPeriodResult;
import com.liuqitech.accountingassistant.dto.analysis.result.BreakdownResult;
import com.liuqitech.accountingassistant.dto.analysis.result.NetCashFlowResult;
import com.liuqitech.accountingassistant.dto.analysis.result.PeriodCompareResult;
import com.liuqitech.accountingassistant.dto.analysis.result.TransactionsResult;
import com.liuqitech.accountingassistant.dto.analysis.result.TrendResult;
import com.liuqitech.accountingassistant.enums.AnalysisComparisonMode;
import com.liuqitech.accountingassistant.enums.AnalysisDimension;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisOperationKind;
import com.liuqitech.accountingassistant.enums.AnalysisPeriodPreset;
import com.liuqitech.accountingassistant.enums.AnalysisQueryKind;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;
import com.liuqitech.accountingassistant.enums.AnalysisUnit;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.service.analysis.AnalysisOperationExecutor;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPeriodResolverImpl;
import com.liuqitech.accountingassistant.service.analysis.AnalysisReadService;
import com.liuqitech.accountingassistant.service.analysis.DerivedMetricCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisOperationExecutorTest {

    private static final Long ledgerId = 100L;

    @Mock
    private AnalysisReadService readService;

    private AnalysisOperationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AnalysisOperationExecutor(
            readService, new AnalysisPeriodResolverImpl(), new DerivedMetricCalculator());
    }

    @Test
    void periodCompareAddsWarningWhenPreviousValueIsZero() {
        when(readService.aggregate(eq(ledgerId), any()))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("10.00"), 1L))
            .thenReturn(new AnalysisAggregateFact(BigDecimal.ZERO, 0L));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, periodComparePlan(), LocalDate.of(2026, 8, 25));
        PeriodCompareResult result = (PeriodCompareResult) outcome.results().get(0);

        assertThat(result.values().changeRate()).isNull();
        assertThat(result.warnings()).extracting(AnalysisWarning::code).contains("ZERO_BASELINE");
    }

    @Test
    void executorReturnsPartialResultWhenOneOfThreeQueriesFails() {
        when(readService.aggregate(eq(ledgerId), any())).thenReturn(
            new AnalysisAggregateFact(new BigDecimal("100.00"), 2L));
        when(readService.breakdown(eq(ledgerId), any())).thenThrow(
            new DataAccessResourceFailureException("locked"));

        NormalizedAnalysisPlan plan = new NormalizedAnalysisPlan(
            AnalysisOperationKind.COMPOSITE,
            null,
            List.of(aggregateQuery(), breakdownQuery(), aggregateQuery()),
            "复合分析");
        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, plan);

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.PARTIAL_RESULT);
        assertThat(outcome.results()).hasSize(2);
    }

    @Test
    void emptyAggregateReturnsNoData() {
        when(readService.aggregate(eq(ledgerId), any()))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("0.00"), 0L));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.AGGREGATE, null, List.of(aggregateQuery()), "合计"));

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.NO_DATA);
        assertThat(outcome.results()).hasSize(1);
        AggregateResult result = (AggregateResult) outcome.results().get(0);
        assertThat(result.values().value()).isEqualByComparingTo("0.00");
        assertThat(result.values().count()).isEqualTo(0L);
        assertThat(result.kind()).isEqualTo(AnalysisResultKind.AGGREGATE);
        assertThat(result.unit()).isEqualTo(AnalysisUnit.CNY);
    }

    @Test
    void emptyBreakdownAndTransactionsReturnNoData() {
        when(readService.breakdown(eq(ledgerId), any()))
            .thenReturn(new AnalysisBreakdownFact(List.of(), new BigDecimal("0.00"), false));

        AnalysisExecutionOutcome breakdown = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.BREAKDOWN, null, List.of(breakdownQuery()), "排行"));
        assertThat(breakdown.status()).isEqualTo(AnalysisResponseStatus.NO_DATA);
        assertThat(breakdown.results()).hasSize(1);
        BreakdownResult breakdownResult = (BreakdownResult) breakdown.results().get(0);
        assertThat(breakdownResult.rows()).isEmpty();
        assertThat(breakdownResult.unit()).isEqualTo(AnalysisUnit.CNY);

        when(readService.transactions(eq(ledgerId), any())).thenReturn(List.of());
        AnalysisExecutionOutcome transactions = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.TRANSACTIONS, null, List.of(transactionsQuery()), "明细"));
        assertThat(transactions.status()).isEqualTo(AnalysisResponseStatus.NO_DATA);
        assertThat(transactions.results()).hasSize(1);
    }

    @Test
    void emptyTrendReturnsNoDataInsteadOfFakePercent() {
        when(readService.trend(eq(ledgerId), any())).thenReturn(new AnalysisTrendFact(List.of(
            new AnalysisTrendFact.Point("2026-01", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0),
            new AnalysisTrendFact.Point("2026-02", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0))));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.TREND, null, List.of(trendQuery()), "趋势"));

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.NO_DATA);
        TrendResult result = (TrendResult) outcome.results().get(0);
        assertThat(result.points()).hasSize(2);
        assertThat(result.unit()).isEqualTo(AnalysisUnit.CNY);
    }

    @Test
    void negativeNetCashFlowKeepsSignedNet() {
        when(readService.aggregate(eq(ledgerId), any())).thenAnswer(invocation -> {
            NormalizedAnalysisQuery query = invocation.getArgument(1);
            if (query.transactionType() == TransactionType.INCOME) {
                return new AnalysisAggregateFact(new BigDecimal("50.00"), 1L);
            }
            return new AnalysisAggregateFact(new BigDecimal("80.00"), 2L);
        });

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, netCashFlowPlan());
        NetCashFlowResult result = (NetCashFlowResult) outcome.results().get(0);

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.OK);
        assertThat(result.values().net()).isEqualByComparingTo("-30.00");
        assertThat(result.values().net().scale()).isEqualTo(2);
        assertThat(result.transactionType()).isNull();
        assertThat(result.metric()).isEqualTo(AnalysisMetric.SUM);
        assertThat(result.unit()).isEqualTo(AnalysisUnit.CNY);
        assertThat(result.period().current()).isEqualTo(new AnalysisDateRange(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)));
        assertThat(result.period().previous()).isNull();
    }

    @Test
    void netCashFlowUsesFirstQueryWindowForBothLegs() {
        when(readService.aggregate(eq(ledgerId), any())).thenReturn(
            new AnalysisAggregateFact(new BigDecimal("10.00"), 1L));

        executor.execute(ledgerId, netCashFlowPlan());

        ArgumentCaptor<NormalizedAnalysisQuery> captor = ArgumentCaptor.forClass(NormalizedAnalysisQuery.class);
        verify(readService, times(2)).aggregate(eq(ledgerId), captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(query ->
            assertThat(query.period()).isEqualTo(new AnalysisDateRange(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25))));
    }

    @Test
    void periodCompareUsesResolvedPreviousRangeAndHalfUpChangeRate() {
        when(readService.aggregate(eq(ledgerId), any()))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("10.00"), 2L))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("3.00"), 1L));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, periodComparePlan(), LocalDate.of(2026, 8, 25));
        PeriodCompareResult result = (PeriodCompareResult) outcome.results().get(0);

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.OK);
        assertThat(result.values().difference()).isEqualByComparingTo("7.00");
        assertThat(result.values().changeRate()).isEqualByComparingTo("233.33");
        assertThat(result.values().changeRate().scale()).isEqualTo(2);
        assertThat(result.unit()).isEqualTo(AnalysisUnit.CNY);
        assertThat(result.period().current()).isEqualTo(new AnalysisDateRange(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)));
        assertThat(result.period().previous()).isEqualTo(new AnalysisDateRange(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 25)));

        ArgumentCaptor<NormalizedAnalysisQuery> captor = ArgumentCaptor.forClass(NormalizedAnalysisQuery.class);
        verify(readService, times(2)).aggregate(eq(ledgerId), captor.capture());
        assertThat(captor.getAllValues().get(0).period()).isEqualTo(new AnalysisDateRange(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)));
        assertThat(captor.getAllValues().get(1).period()).isEqualTo(new AnalysisDateRange(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 25)));
    }

    @Test
    void periodCompareUsesClockTodayNotStoredRangeEnd() {
        when(readService.aggregate(eq(ledgerId), any()))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("10.00"), 1L))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("4.00"), 1L));

        AnalysisExecutionOutcome outcome = executor.execute(
            ledgerId, periodComparePlan(), LocalDate.of(2026, 9, 15));
        PeriodCompareResult result = (PeriodCompareResult) outcome.results().get(0);

        assertThat(result.period().current()).isEqualTo(new AnalysisDateRange(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)));
        assertThat(result.period().previous()).isEqualTo(new AnalysisDateRange(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15)));
    }

    @Test
    void averageByPeriodIncludesZeroBuckets() {
        when(readService.trend(eq(ledgerId), any())).thenReturn(new AnalysisTrendFact(List.of(
            new AnalysisTrendFact.Point("2026-01", BigDecimal.ZERO, new BigDecimal("100.00"),
                new BigDecimal("100.00"), 1),
            new AnalysisTrendFact.Point("2026-02", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0),
            new AnalysisTrendFact.Point("2026-03", BigDecimal.ZERO, new BigDecimal("50.00"),
                new BigDecimal("50.00"), 1))));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.AVERAGE_BY_PERIOD, null, List.of(trendQuery()), "月均"));
        AverageByPeriodResult result = (AverageByPeriodResult) outcome.results().get(0);

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.OK);
        assertThat(result.values().average()).isEqualByComparingTo("50.00");
        assertThat(result.points()).hasSize(3);
    }

    @Test
    void queryFailedWhenAllQueriesFail() {
        when(readService.aggregate(eq(ledgerId), any()))
            .thenThrow(new DataAccessResourceFailureException("locked"));
        when(readService.breakdown(eq(ledgerId), any()))
            .thenThrow(new DataAccessResourceFailureException("locked"));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.COMPOSITE,
            null,
            List.of(aggregateQuery(), breakdownQuery()),
            "复合分析"));

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.QUERY_FAILED);
        assertThat(outcome.results()).isEmpty();
    }

    @Test
    void periodCompareKeepsDerivedResultWhenPreviousLegFails() {
        when(readService.aggregate(eq(ledgerId), any()))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("10.00"), 1L))
            .thenThrow(new DataAccessResourceFailureException("locked"));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, periodComparePlan(), LocalDate.of(2026, 8, 25));
        PeriodCompareResult result = (PeriodCompareResult) outcome.results().get(0);

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.PARTIAL_RESULT);
        assertThat(outcome.results()).hasSize(1);
        assertThat(result).isInstanceOf(PeriodCompareResult.class);
        assertThat(result.values().current()).isEqualByComparingTo("10.00");
        assertThat(result.values().previous()).isEqualByComparingTo("0.00");
        assertThat(result.values().changeRate()).isNull();
        assertThat(result.warnings()).extracting(AnalysisWarning::code)
            .contains("ZERO_BASELINE", "QUERY_FAILED");
        assertThat(result.warnings()).filteredOn(warning -> "QUERY_FAILED".equals(warning.code()))
            .extracting(AnalysisWarning::message)
            .anySatisfy(message -> assertThat(message).contains("上一期间"));
    }

    @Test
    void periodCompareKeepsDerivedResultWhenCurrentLegFails() {
        when(readService.aggregate(eq(ledgerId), any()))
            .thenThrow(new DataAccessResourceFailureException("locked"))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("5.00"), 1L));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, periodComparePlan(), LocalDate.of(2026, 8, 25));
        PeriodCompareResult result = (PeriodCompareResult) outcome.results().get(0);

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.PARTIAL_RESULT);
        assertThat(outcome.results()).hasSize(1);
        assertThat(result).isInstanceOf(PeriodCompareResult.class);
        assertThat(result.values().current()).isEqualByComparingTo("0.00");
        assertThat(result.values().previous()).isEqualByComparingTo("5.00");
        assertThat(result.warnings()).extracting(AnalysisWarning::code).contains("QUERY_FAILED");
        assertThat(result.warnings()).filteredOn(warning -> "QUERY_FAILED".equals(warning.code()))
            .extracting(AnalysisWarning::message)
            .anySatisfy(message -> assertThat(message).contains("当前期间"));
    }

    @Test
    void netCashFlowKeepsDerivedResultWhenExpenseLegFails() {
        when(readService.aggregate(eq(ledgerId), any())).thenAnswer(invocation -> {
            NormalizedAnalysisQuery query = invocation.getArgument(1);
            if (query.transactionType() == TransactionType.INCOME) {
                return new AnalysisAggregateFact(new BigDecimal("50.00"), 1L);
            }
            throw new DataAccessResourceFailureException("locked");
        });

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, netCashFlowPlan());
        NetCashFlowResult result = (NetCashFlowResult) outcome.results().get(0);

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.PARTIAL_RESULT);
        assertThat(outcome.results()).hasSize(1);
        assertThat(result.values().income()).isEqualByComparingTo("50.00");
        assertThat(result.values().expense()).isEqualByComparingTo("0.00");
        assertThat(result.values().net()).isEqualByComparingTo("50.00");
        assertThat(result.warnings()).extracting(AnalysisWarning::code).contains("QUERY_FAILED");
        assertThat(result.warnings()).filteredOn(warning -> "QUERY_FAILED".equals(warning.code()))
            .extracting(AnalysisWarning::message)
            .anySatisfy(message -> assertThat(message).contains("支出"));
    }

    @Test
    void breakdownUnitIsCnyNotPercentAndKeepsAssociationShare() {
        when(readService.breakdown(eq(ledgerId), any())).thenReturn(new AnalysisBreakdownFact(List.of(
            new AnalysisBreakdownFact.Row(1L, "旅行", null, new BigDecimal("60.00"), 2),
            new AnalysisBreakdownFact.Row(2L, "餐饮", null, new BigDecimal("40.00"), 1)),
            new BigDecimal("100.00"), true));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.BREAKDOWN, null, List.of(tagBreakdownQuery()), "标签占比"));
        BreakdownResult result = (BreakdownResult) outcome.results().get(0);

        assertThat(result.unit()).isEqualTo(AnalysisUnit.CNY);
        assertThat(result.associationShare()).isTrue();
        assertThat(result.rows().get(0).percentage()).isEqualByComparingTo("60.00");
    }

    @Test
    void countMetricMapsToCountUnit() {
        when(readService.aggregate(eq(ledgerId), any()))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("4.00"), 4L));

        NormalizedAnalysisQuery query = new NormalizedAnalysisQuery(
            "q1",
            AnalysisQueryKind.AGGREGATE,
            AnalysisMetric.COUNT,
            TransactionType.EXPENSE,
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
            new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)),
            AnalysisFilters.empty(),
            null,
            null,
            AnalysisSortField.AMOUNT,
            AnalysisSortDirection.DESC,
            10,
            "笔数");
        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.AGGREGATE, null, List.of(query), "笔数"));

        AggregateResult result = (AggregateResult) outcome.results().get(0);
        assertThat(result.unit()).isEqualTo(AnalysisUnit.COUNT);
        assertThat(result.metric()).isEqualTo(AnalysisMetric.COUNT);
    }

    @Test
    void transactionsCountsDisplayedRowsWithoutOmitting() {
        when(readService.transactions(eq(ledgerId), any())).thenReturn(List.of(
            new AnalysisTransactionFact(1L, new BigDecimal("10.00"), TransactionType.EXPENSE,
                LocalDate.of(2026, 8, 2), "午餐", "餐饮", "生活", "支付宝", null),
            new AnalysisTransactionFact(2L, new BigDecimal("20.00"), TransactionType.EXPENSE,
                LocalDate.of(2026, 8, 3), "公交", "交通", "出行", "支付宝", null)));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.TRANSACTIONS, null, List.of(transactionsQuery()), "明细"));
        TransactionsResult result = (TransactionsResult) outcome.results().get(0);

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.OK);
        assertThat(result.values().totalCount()).isEqualTo(2L);
        assertThat(result.values().displayedCount()).isEqualTo(2);
        assertThat(result.values().detailsOmitted()).isFalse();
        assertThat(result.transactions()).hasSize(2);
    }

    @Test
    void compositeKeepsEmptyResultWhenSiblingHasData() {
        when(readService.aggregate(eq(ledgerId), any()))
            .thenReturn(new AnalysisAggregateFact(new BigDecimal("100.00"), 2L));
        when(readService.breakdown(eq(ledgerId), any()))
            .thenReturn(new AnalysisBreakdownFact(List.of(), new BigDecimal("0.00"), false));

        AnalysisExecutionOutcome outcome = executor.execute(ledgerId, new NormalizedAnalysisPlan(
            AnalysisOperationKind.COMPOSITE,
            null,
            List.of(aggregateQuery(), breakdownQuery()),
            "复合分析"));

        assertThat(outcome.status()).isEqualTo(AnalysisResponseStatus.OK);
        assertThat(outcome.results()).hasSize(2);
        assertThat(outcome.results().get(0)).isInstanceOf(AggregateResult.class);
        assertThat(outcome.results().get(1)).isInstanceOf(BreakdownResult.class);
    }

    private NormalizedAnalysisQuery aggregateQuery() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 25);
        return new NormalizedAnalysisQuery(
            "q1",
            AnalysisQueryKind.AGGREGATE,
            AnalysisMetric.SUM,
            TransactionType.EXPENSE,
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
            new AnalysisDateRange(start, end),
            AnalysisFilters.empty(),
            null,
            null,
            AnalysisSortField.AMOUNT,
            AnalysisSortDirection.DESC,
            10,
            "支出合计");
    }

    private NormalizedAnalysisQuery breakdownQuery() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 25);
        return new NormalizedAnalysisQuery(
            "q2",
            AnalysisQueryKind.BREAKDOWN,
            AnalysisMetric.SUM,
            TransactionType.EXPENSE,
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
            new AnalysisDateRange(start, end),
            AnalysisFilters.empty(),
            AnalysisDimension.PARENT_CATEGORY,
            null,
            AnalysisSortField.AMOUNT,
            AnalysisSortDirection.DESC,
            10,
            "分类排行");
    }

    private NormalizedAnalysisPlan periodComparePlan() {
        NormalizedAnalysisQuery current = aggregateQuery();
        NormalizedAnalysisQuery previous = new NormalizedAnalysisQuery(
            "q2",
            current.kind(),
            current.metric(),
            current.transactionType(),
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.PREVIOUS_MONTH),
            new AnalysisDateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
            current.filters(),
            current.dimension(),
            current.timeGrain(),
            current.sortField(),
            current.sortDirection(),
            current.limit(),
            current.title());
        return new NormalizedAnalysisPlan(
            AnalysisOperationKind.PERIOD_COMPARE,
            AnalysisComparisonMode.PREVIOUS_PERIOD,
            List.of(current, previous),
            "期间对比");
    }

    private NormalizedAnalysisQuery trendQuery() {
        return new NormalizedAnalysisQuery(
            "q3",
            AnalysisQueryKind.TREND,
            AnalysisMetric.SUM,
            TransactionType.EXPENSE,
            AnalysisPeriodSpec.explicit(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
            new AnalysisDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
            AnalysisFilters.empty(),
            null,
            AnalysisTimeGrain.MONTH,
            AnalysisSortField.DATE,
            AnalysisSortDirection.ASC,
            10,
            "月趋势");
    }

    private NormalizedAnalysisQuery transactionsQuery() {
        return new NormalizedAnalysisQuery(
            "q4",
            AnalysisQueryKind.TRANSACTIONS,
            AnalysisMetric.SUM,
            TransactionType.EXPENSE,
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
            new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)),
            AnalysisFilters.empty(),
            null,
            null,
            AnalysisSortField.DATE,
            AnalysisSortDirection.DESC,
            10,
            "明细");
    }

    private NormalizedAnalysisQuery tagBreakdownQuery() {
        NormalizedAnalysisQuery base = breakdownQuery();
        return new NormalizedAnalysisQuery(
            base.id(),
            base.kind(),
            base.metric(),
            base.transactionType(),
            base.periodSemantic(),
            base.period(),
            base.filters(),
            AnalysisDimension.TAG,
            base.timeGrain(),
            base.sortField(),
            base.sortDirection(),
            base.limit(),
            "标签占比");
    }

    private NormalizedAnalysisPlan netCashFlowPlan() {
        NormalizedAnalysisQuery income = new NormalizedAnalysisQuery(
            "q-income",
            AnalysisQueryKind.AGGREGATE,
            AnalysisMetric.SUM,
            TransactionType.INCOME,
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
            new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)),
            AnalysisFilters.empty(),
            null,
            null,
            AnalysisSortField.AMOUNT,
            AnalysisSortDirection.DESC,
            10,
            "收入");
        NormalizedAnalysisQuery expense = new NormalizedAnalysisQuery(
            "q-expense",
            AnalysisQueryKind.AGGREGATE,
            AnalysisMetric.SUM,
            TransactionType.EXPENSE,
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.PREVIOUS_MONTH),
            new AnalysisDateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
            AnalysisFilters.empty(),
            null,
            null,
            AnalysisSortField.AMOUNT,
            AnalysisSortDirection.DESC,
            10,
            "支出");
        return new NormalizedAnalysisPlan(
            AnalysisOperationKind.NET_CASH_FLOW, null, List.of(income, expense), "净现金流");
    }
}
