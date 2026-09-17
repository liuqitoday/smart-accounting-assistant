package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisBreakdownFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import com.liuqitech.accountingassistant.service.analysis.DerivedMetricCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DerivedMetricCalculatorTest {

    private final DerivedMetricCalculator calculator = new DerivedMetricCalculator();

    @Test
    void zeroPreviousMakesChangeRateUndefinedAndDifferenceKeepsScale() {
        assertThat(calculator.changeRate(new BigDecimal("10.00"), BigDecimal.ZERO))
            .isNull();
        assertThat(calculator.difference(new BigDecimal("10.00"), new BigDecimal("3.25")))
            .isEqualByComparingTo("6.75");
    }

    @Test
    void averageByPeriodIncludesZeroBucketsButAveragePerTransactionDoesNotReuseIt() {
        DerivedMetricCalculator.AverageByPeriodValues result = calculator.averageByPeriod(List.of(
            new AnalysisTrendFact.Point("2026-01", BigDecimal.ZERO, new BigDecimal("100.00"),
                new BigDecimal("100.00"), 1),
            new AnalysisTrendFact.Point("2026-02", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0),
            new AnalysisTrendFact.Point("2026-03", BigDecimal.ZERO, new BigDecimal("50.00"),
                new BigDecimal("50.00"), 1)));

        assertThat(result.average()).isEqualByComparingTo("50.00");
    }

    @Test
    void tagShareUsesSumOfTagRowsAndIsMarkedAssociationShare() {
        AnalysisBreakdownFact fact = new AnalysisBreakdownFact(List.of(
            new AnalysisBreakdownFact.Row(1L, "旅行", null, new BigDecimal("60.00"), 2),
            new AnalysisBreakdownFact.Row(2L, "餐饮", null, new BigDecimal("40.00"), 1)),
            new BigDecimal("100.00"), true);

        DerivedMetricCalculator.BreakdownValues result = calculator.withShares(fact);

        assertThat(result.associationShare()).isTrue();
        assertThat(result.rows().get(0).percentage()).isEqualByComparingTo("60.00");
    }

    @Test
    void topNShareUsesAllMatchingGroupsAsDenominator() {
        AnalysisBreakdownFact fact = new AnalysisBreakdownFact(List.of(
            new AnalysisBreakdownFact.Row(1L, "生活", null, new BigDecimal("100.00"), 2),
            new AnalysisBreakdownFact.Row(2L, "交通", null, new BigDecimal("60.00"), 1)),
            new BigDecimal("200.00"), false); // 未展示的第三组仍贡献 40.00 分母

        DerivedMetricCalculator.BreakdownValues result = calculator.withShares(fact);

        assertThat(result.rows().get(0).percentage()).isEqualByComparingTo("50.00");
        assertThat(result.rows().get(1).percentage()).isEqualByComparingTo("30.00");
    }

    @Test
    void changeRateUsesHalfUpPercentScale() {
        BigDecimal rate = calculator.changeRate(new BigDecimal("10.00"), new BigDecimal("3.00"));

        assertThat(rate).isEqualByComparingTo("233.33");
        assertThat(rate.scale()).isEqualTo(2);
        assertThat(calculator.changeRate(new BigDecimal("10.00"), new BigDecimal("5.00")))
            .isEqualByComparingTo("100.00");
    }

    @Test
    void differenceUsesHalfUpScale() {
        BigDecimal diff = calculator.difference(new BigDecimal("10.005"), new BigDecimal("3.001"));

        assertThat(diff).isEqualByComparingTo("7.00");
        assertThat(diff.scale()).isEqualTo(2);
    }

    @Test
    void netCashFlowCanBeNegative() {
        DerivedMetricCalculator.NetCashFlowValues result =
            calculator.netCashFlow(new BigDecimal("50.00"), new BigDecimal("80.00"));

        assertThat(result.income()).isEqualByComparingTo("50.00");
        assertThat(result.expense()).isEqualByComparingTo("80.00");
        assertThat(result.net()).isEqualByComparingTo("-30.00");
        assertThat(result.net().scale()).isEqualTo(2);
    }

    @Test
    void breakdownPercentageIsNullWhenDenominatorIsZero() {
        AnalysisBreakdownFact fact = new AnalysisBreakdownFact(List.of(
            new AnalysisBreakdownFact.Row(1L, "生活", null, BigDecimal.ZERO, 0)),
            BigDecimal.ZERO, false);

        DerivedMetricCalculator.BreakdownValues result = calculator.withShares(fact);

        assertThat(result.rows().get(0).percentage()).isNull();
    }

    @Test
    void breakdownShareUsesHalfUpPercentageScale() {
        AnalysisBreakdownFact fact = new AnalysisBreakdownFact(List.of(
            new AnalysisBreakdownFact.Row(1L, "A", null, new BigDecimal("1.00"), 1),
            new AnalysisBreakdownFact.Row(2L, "B", null, new BigDecimal("2.00"), 1)),
            new BigDecimal("3.00"), false);

        DerivedMetricCalculator.BreakdownValues result = calculator.withShares(fact);

        assertThat(result.rows().get(0).percentage()).isEqualByComparingTo("33.33");
        assertThat(result.rows().get(1).percentage()).isEqualByComparingTo("66.67");
        assertThat(result.rows().get(0).percentage().scale()).isEqualTo(2);
    }

    @Test
    void averageByPeriodUsesHalfUpAndIncludesEveryBucket() {
        DerivedMetricCalculator.AverageByPeriodValues result = calculator.averageByPeriod(List.of(
            new AnalysisTrendFact.Point("2026-01", BigDecimal.ZERO, new BigDecimal("1.00"),
                new BigDecimal("1.00"), 1),
            new AnalysisTrendFact.Point("2026-02", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0),
            new AnalysisTrendFact.Point("2026-03", BigDecimal.ZERO, new BigDecimal("1.00"),
                new BigDecimal("1.00"), 1)));

        assertThat(result.average()).isEqualByComparingTo("0.67");
        assertThat(result.average().scale()).isEqualTo(2);
        assertThat(result.points()).hasSize(3);
    }
}
