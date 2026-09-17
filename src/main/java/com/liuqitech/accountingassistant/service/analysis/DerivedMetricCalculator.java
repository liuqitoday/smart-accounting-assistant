package com.liuqitech.accountingassistant.service.analysis;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisBreakdownFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class DerivedMetricCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public record NetCashFlowValues(BigDecimal income, BigDecimal expense, BigDecimal net) {}

    public record BreakdownValues(List<Row> rows, boolean associationShare) {
        public BreakdownValues {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        public record Row(Long id, String label, String parentLabel,
                          BigDecimal value, long count, BigDecimal percentage) {}
    }

    public record AverageByPeriodValues(List<AnalysisTrendFact.Point> points, BigDecimal average) {
        public AverageByPeriodValues {
            points = points == null ? List.of() : List.copyOf(points);
        }
    }

    public BigDecimal changeRate(BigDecimal current, BigDecimal previous) {
        if (isZero(previous)) {
            return null;
        }
        return money(current).subtract(previous)
            .multiply(HUNDRED)
            .divide(previous, SCALE, ROUNDING);
    }

    public BigDecimal difference(BigDecimal current, BigDecimal previous) {
        return nullable(current).subtract(nullable(previous)).setScale(SCALE, ROUNDING);
    }

    public NetCashFlowValues netCashFlow(BigDecimal income, BigDecimal expense) {
        BigDecimal scaledIncome = money(income);
        BigDecimal scaledExpense = money(expense);
        return new NetCashFlowValues(scaledIncome, scaledExpense, scaledIncome.subtract(scaledExpense));
    }

    public BreakdownValues withShares(AnalysisBreakdownFact fact) {
        List<AnalysisBreakdownFact.Row> source =
            fact == null || fact.rows() == null ? List.of() : fact.rows();
        BigDecimal denominator = fact == null ? ZERO : fact.denominator();
        boolean zeroDenominator = isZero(denominator);
        List<BreakdownValues.Row> rows = source.stream()
            .map(row -> new BreakdownValues.Row(
                row.id(),
                row.label(),
                row.parentLabel(),
                money(row.value()),
                row.count(),
                zeroDenominator ? null : percentage(row.value(), denominator)))
            .toList();
        boolean associationShare = fact != null && fact.associationShare();
        return new BreakdownValues(rows, associationShare);
    }

    public AverageByPeriodValues averageByPeriod(List<AnalysisTrendFact.Point> points) {
        List<AnalysisTrendFact.Point> source = points == null ? List.of() : points;
        if (source.isEmpty()) {
            return new AverageByPeriodValues(List.of(), ZERO);
        }
        BigDecimal sum = source.stream()
            .map(point -> nullable(point.value()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(source.size()), SCALE, ROUNDING);
        return new AverageByPeriodValues(source, average);
    }

    private static BigDecimal percentage(BigDecimal value, BigDecimal denominator) {
        return nullable(value).multiply(HUNDRED).divide(denominator, SCALE, ROUNDING);
    }

    private static boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return nullable(value).setScale(SCALE, ROUNDING);
    }

    private static BigDecimal nullable(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
