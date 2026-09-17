package com.liuqitech.accountingassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisExecutionOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFollowUp;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPayloadV2;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResult;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResultPeriod;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTransactionFact;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPeriod;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.result.AggregateResult;
import com.liuqitech.accountingassistant.dto.analysis.result.AverageByPeriodResult;
import com.liuqitech.accountingassistant.dto.analysis.result.BreakdownResult;
import com.liuqitech.accountingassistant.dto.analysis.result.NetCashFlowResult;
import com.liuqitech.accountingassistant.dto.analysis.result.PeriodCompareResult;
import com.liuqitech.accountingassistant.dto.analysis.result.TransactionsResult;
import com.liuqitech.accountingassistant.dto.analysis.result.TrendResult;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisOperationKind;
import com.liuqitech.accountingassistant.enums.AnalysisPeriodPreset;
import com.liuqitech.accountingassistant.enums.AnalysisQueryKind;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.AnalysisUnit;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.service.analysis.AnalysisFollowUpService;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPayloadFactory;
import com.liuqitech.accountingassistant.service.analysis.DerivedMetricCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisPayloadFactoryTest {

    private static final AnalysisDateRange RANGE =
        new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25));
    private static final AnalysisResultPeriod PERIOD = new AnalysisResultPeriod(RANGE, null);

    private ObjectMapper objectMapper;
    private AnalysisPayloadFactory factory;
    private AnalysisFollowUpService followUpService;
    private RawAnalysisPlan rawPlan;
    private NormalizedAnalysisPlan normalizedPlan;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        followUpService = new AnalysisFollowUpService();
        factory = new AnalysisPayloadFactory(objectMapper, followUpService);
        rawPlan = sampleRawPlan();
        normalizedPlan = sampleNormalizedPlan();
    }

    @Test
    void payloadUsesNamedRowsAndPreservesCnyCountAndPercentUnits() throws Exception {
        AnalysisPayloadV2 payload = factory.create(rawPlan, normalizedPlan, outcomeWithAggregateAndBreakdown());

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(payload));

        assertThat(json.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(json.at("/results/0/unit").asText()).isEqualTo("CNY");
        assertThat(json.at("/results/1/unit").asText()).isEqualTo("CNY");
        assertThat(json.at("/results/1/rows/0/percentage").decimalValue())
            .isEqualByComparingTo("60.00");
    }

    @Test
    void oversizedTransactionsBecomeSummaryWarningWithoutInvalidJson() throws Exception {
        AnalysisPayloadV2 payload = factory.create(rawPlan, normalizedPlan, outcomeWithLargeTransactions(50_000));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(payload));

        assertThat(objectMapper.writeValueAsBytes(payload)).hasSizeLessThanOrEqualTo(256 * 1024);
        assertThat(json.at("/warnings").toString()).contains("PAYLOAD");
    }

    @Test
    void followUpsDependOnlyOnNormalizedPlanAndResultKind() {
        List<AnalysisFollowUp> followUps = followUpService.suggest(normalizedPlan,
            outcomeWithKind(AnalysisResultKind.BREAKDOWN));

        assertThat(followUps).extracting(AnalysisFollowUp::question)
            .contains("按账户拆分", "查看明细");
    }

    private AnalysisExecutionOutcome outcomeWithLargeTransactions(int descriptionLength) {
        String blob = "x".repeat(descriptionLength);
        List<AnalysisTransactionFact> transactions = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            transactions.add(new AnalysisTransactionFact(
                (long) i + 1,
                new BigDecimal("10.00"),
                TransactionType.EXPENSE,
                LocalDate.of(2026, 8, 1),
                blob,
                "餐饮",
                "生活",
                "支付宝",
                blob));
        }
        TransactionsResult result = new TransactionsResult(
            "明细",
            AnalysisMetric.SUM,
            AnalysisUnit.CNY,
            TransactionType.EXPENSE,
            PERIOD,
            AnalysisFilters.empty(),
            List.of(),
            new TransactionsResult.Values(50, 50, false),
            transactions);
        return new AnalysisExecutionOutcome(AnalysisResponseStatus.OK, List.of(result), List.of());
    }

    private AnalysisExecutionOutcome outcomeWithAggregateAndBreakdown() {
        AggregateResult aggregate = new AggregateResult(
            "支出合计",
            AnalysisMetric.SUM,
            AnalysisUnit.CNY,
            TransactionType.EXPENSE,
            PERIOD,
            AnalysisFilters.empty(),
            List.of(),
            new AggregateResult.Values(new BigDecimal("100.00"), 3L));
        BreakdownResult breakdown = new BreakdownResult(
            "分类排行",
            AnalysisMetric.SUM,
            AnalysisUnit.CNY,
            TransactionType.EXPENSE,
            PERIOD,
            AnalysisFilters.empty(),
            List.of(),
            List.of(
                new DerivedMetricCalculator.BreakdownValues.Row(
                    1L, "餐饮", "生活", new BigDecimal("60.00"), 2L, new BigDecimal("60.00")),
                new DerivedMetricCalculator.BreakdownValues.Row(
                    2L, "交通", "出行", new BigDecimal("40.00"), 1L, new BigDecimal("40.00"))),
            false);
        return new AnalysisExecutionOutcome(
            AnalysisResponseStatus.OK, List.of(aggregate, breakdown), List.of());
    }

    private AnalysisExecutionOutcome outcomeWithKind(AnalysisResultKind kind) {
        AnalysisResult result = switch (kind) {
            case AGGREGATE -> new AggregateResult(
                "支出合计", AnalysisMetric.SUM, AnalysisUnit.CNY, TransactionType.EXPENSE,
                PERIOD, AnalysisFilters.empty(), List.of(),
                new AggregateResult.Values(new BigDecimal("10.00"), 1L));
            case PERIOD_COMPARE -> new PeriodCompareResult(
                "期间对比", AnalysisMetric.SUM, AnalysisUnit.CNY, TransactionType.EXPENSE,
                PERIOD, AnalysisFilters.empty(), List.of(),
                new PeriodCompareResult.Values(
                    new BigDecimal("10.00"), new BigDecimal("5.00"),
                    new BigDecimal("5.00"), new BigDecimal("100.00")));
            case NET_CASH_FLOW -> new NetCashFlowResult(
                "净现金流", AnalysisMetric.SUM, AnalysisUnit.CNY, null,
                PERIOD, AnalysisFilters.empty(), List.of(),
                new DerivedMetricCalculator.NetCashFlowValues(
                    new BigDecimal("20.00"), new BigDecimal("10.00"), new BigDecimal("10.00")));
            case BREAKDOWN -> new BreakdownResult(
                "分类排行", AnalysisMetric.SUM, AnalysisUnit.CNY, TransactionType.EXPENSE,
                PERIOD, AnalysisFilters.empty(), List.of(),
                List.of(new DerivedMetricCalculator.BreakdownValues.Row(
                    1L, "餐饮", "生活", new BigDecimal("60.00"), 2L, new BigDecimal("60.00"))),
                false);
            case TREND -> new TrendResult(
                "月趋势", AnalysisMetric.SUM, AnalysisUnit.CNY, TransactionType.EXPENSE,
                PERIOD, AnalysisFilters.empty(), List.of(), List.of());
            case AVERAGE_BY_PERIOD -> new AverageByPeriodResult(
                "月均", AnalysisMetric.AVG, AnalysisUnit.CNY, TransactionType.EXPENSE,
                PERIOD, AnalysisFilters.empty(), List.of(), List.of(),
                new AverageByPeriodResult.Values(new BigDecimal("10.00")));
            case TRANSACTIONS -> new TransactionsResult(
                "明细", AnalysisMetric.SUM, AnalysisUnit.CNY, TransactionType.EXPENSE,
                PERIOD, AnalysisFilters.empty(), List.of(),
                new TransactionsResult.Values(0, 0, false), List.of());
        };
        return new AnalysisExecutionOutcome(AnalysisResponseStatus.OK, List.of(result), List.of());
    }

    private RawAnalysisPlan sampleRawPlan() {
        RawAnalysisPlan plan = new RawAnalysisPlan();
        plan.setOperation("AGGREGATE");
        plan.setTitle("支出合计");
        RawAnalysisQuery query = new RawAnalysisQuery();
        query.setId("q1");
        query.setKind("AGGREGATE");
        query.setMetric("SUM");
        query.setTransactionType("EXPENSE");
        query.setTitle("支出合计");
        RawAnalysisPeriod period = new RawAnalysisPeriod();
        period.setPreset("CURRENT_MONTH");
        query.setPeriod(period);
        plan.setQueries(List.of(query));
        return plan;
    }

    private NormalizedAnalysisPlan sampleNormalizedPlan() {
        return new NormalizedAnalysisPlan(
            AnalysisOperationKind.AGGREGATE,
            null,
            List.of(new NormalizedAnalysisQuery(
                "q1",
                AnalysisQueryKind.AGGREGATE,
                AnalysisMetric.SUM,
                TransactionType.EXPENSE,
                AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
                RANGE,
                AnalysisFilters.empty(),
                null,
                null,
                AnalysisSortField.AMOUNT,
                AnalysisSortDirection.DESC,
                10,
                "支出合计")),
            "支出合计");
    }
}
