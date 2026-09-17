package com.liuqitech.accountingassistant.service.analysis;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisAggregateFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisBreakdownFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisComparisonRanges;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisExecutionOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResult;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResultPeriod;
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
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.enums.AnalysisUnit;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.util.AppClock;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
public class AnalysisOperationExecutor {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final AnalysisWarning ZERO_BASELINE =
        new AnalysisWarning("ZERO_BASELINE", "上一期间为 0，变化率不可计算");

    private final AnalysisReadService readService;
    private final AnalysisPeriodResolver periodResolver;
    private final DerivedMetricCalculator calculator;

    public AnalysisOperationExecutor(AnalysisReadService readService,
                                     AnalysisPeriodResolver periodResolver,
                                     DerivedMetricCalculator calculator) {
        this.readService = readService;
        this.periodResolver = periodResolver;
        this.calculator = calculator;
    }

    public AnalysisExecutionOutcome execute(Long ledgerId, NormalizedAnalysisPlan plan) {
        return execute(ledgerId, plan, AppClock.today());
    }

    public AnalysisExecutionOutcome execute(Long ledgerId, NormalizedAnalysisPlan plan, LocalDate today) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(plan.operation(), "operation");
        Objects.requireNonNull(today, "today");
        return switch (plan.operation()) {
            case AGGREGATE, BREAKDOWN, TREND, TRANSACTIONS, AVERAGE_BY_PERIOD ->
                executeSingle(ledgerId, plan);
            case PERIOD_COMPARE -> executePeriodCompare(ledgerId, plan, today);
            case NET_CASH_FLOW -> executeNetCashFlow(ledgerId, plan);
            case COMPOSITE -> executeComposite(ledgerId, plan);
        };
    }

    private AnalysisExecutionOutcome executeSingle(Long ledgerId, NormalizedAnalysisPlan plan) {
        if (plan.queries().isEmpty()) {
            return new AnalysisExecutionOutcome(AnalysisResponseStatus.NO_DATA, List.of(), List.of());
        }
        ExecutionAcc acc = new ExecutionAcc();
        NormalizedAnalysisQuery query = plan.queries().get(0);
        QueryAttempt<AnalysisResult> attempt = attempt(() -> switch (plan.operation()) {
            case AVERAGE_BY_PERIOD -> averageByPeriodResult(ledgerId, plan, query);
            default -> baseResult(ledgerId, plan, query);
        }, acc.warnings);
        if (attempt.failed) {
            acc.failed++;
        } else {
            acc.succeeded++;
            acc.results.add(attempt.value);
            if (!isEmpty(attempt.value)) {
                acc.withData++;
            }
        }
        return acc.toOutcome();
    }

    private AnalysisExecutionOutcome executeComposite(Long ledgerId, NormalizedAnalysisPlan plan) {
        ExecutionAcc acc = new ExecutionAcc();
        for (NormalizedAnalysisQuery query : plan.queries()) {
            QueryAttempt<AnalysisResult> attempt = attempt(() -> baseResult(ledgerId, plan, query), acc.warnings);
            if (attempt.failed) {
                acc.failed++;
                continue;
            }
            acc.succeeded++;
            acc.results.add(attempt.value);
            if (!isEmpty(attempt.value)) {
                acc.withData++;
            }
        }
        return acc.toOutcome();
    }

    private AnalysisExecutionOutcome executePeriodCompare(Long ledgerId, NormalizedAnalysisPlan plan, LocalDate today) {
        NormalizedAnalysisQuery base = plan.queries().get(0);
        AnalysisComparisonRanges ranges = periodResolver.resolveCompare(
            base.periodSemantic(),
            Objects.requireNonNull(plan.comparisonMode(), "comparisonMode"),
            today);
        AnalysisDateRange currentRange = ranges.current();
        AnalysisDateRange previousRange = ranges.previous();

        ExecutionAcc acc = new ExecutionAcc();
        QueryAttempt<AnalysisAggregateFact> currentAttempt =
            attempt(() -> readService.aggregate(ledgerId, withPeriod(base, currentRange)), acc.warnings);
        QueryAttempt<AnalysisAggregateFact> previousAttempt =
            attempt(() -> readService.aggregate(ledgerId, withPeriod(base, previousRange)), acc.warnings);

        if (currentAttempt.failed) {
            acc.failed++;
        } else {
            acc.succeeded++;
        }
        if (previousAttempt.failed) {
            acc.failed++;
        } else {
            acc.succeeded++;
        }

        if (acc.succeeded == 0) {
            return acc.toOutcome();
        }

        AnalysisAggregateFact currentFact = currentAttempt.failed
            ? new AnalysisAggregateFact(ZERO, 0L)
            : currentAttempt.value;
        AnalysisAggregateFact previousFact = previousAttempt.failed
            ? new AnalysisAggregateFact(ZERO, 0L)
            : previousAttempt.value;
        List<AnalysisWarning> resultWarnings = new ArrayList<>();
        if (currentAttempt.failed) {
            resultWarnings.add(new AnalysisWarning("QUERY_FAILED", "当前期间查询失败"));
        }
        if (previousAttempt.failed) {
            resultWarnings.add(new AnalysisWarning("QUERY_FAILED", "上一期间查询失败"));
        }
        BigDecimal changeRate = calculator.changeRate(currentFact.amount(), previousFact.amount());
        if (changeRate == null) {
            resultWarnings.add(ZERO_BASELINE);
        }
        PeriodCompareResult result = new PeriodCompareResult(
            title(plan, base),
            metric(base),
            unit(base),
            base.transactionType(),
            new AnalysisResultPeriod(currentRange, previousRange),
            filters(base),
            resultWarnings,
            new PeriodCompareResult.Values(
                scale(currentFact.amount()),
                scale(previousFact.amount()),
                calculator.difference(currentFact.amount(), previousFact.amount()),
                changeRate));
        acc.results.add(result);
        if (!isEmpty(currentFact) || !isEmpty(previousFact)) {
            acc.withData++;
        }
        return acc.toOutcome();
    }

    private AnalysisExecutionOutcome executeNetCashFlow(Long ledgerId, NormalizedAnalysisPlan plan) {
        AnalysisDateRange window = plan.queries().get(0).period();
        ExecutionAcc acc = new ExecutionAcc();
        BigDecimal income = ZERO;
        BigDecimal expense = ZERO;
        long incomeCount = 0L;
        long expenseCount = 0L;
        NormalizedAnalysisQuery first = plan.queries().get(0);
        List<AnalysisWarning> resultWarnings = new ArrayList<>();

        for (NormalizedAnalysisQuery query : plan.queries()) {
            QueryAttempt<AnalysisAggregateFact> attempt =
                attempt(() -> readService.aggregate(ledgerId, withPeriod(query, window)), acc.warnings);
            if (attempt.failed) {
                acc.failed++;
                resultWarnings.add(new AnalysisWarning("QUERY_FAILED", failedCashFlowLegMessage(query)));
                continue;
            }
            acc.succeeded++;
            AnalysisAggregateFact fact = attempt.value;
            if (query.transactionType() == TransactionType.INCOME) {
                income = scale(fact.amount());
                incomeCount = fact.count();
            } else if (query.transactionType() == TransactionType.EXPENSE) {
                expense = scale(fact.amount());
                expenseCount = fact.count();
            }
        }

        if (acc.succeeded == 0) {
            return acc.toOutcome();
        }

        DerivedMetricCalculator.NetCashFlowValues values = calculator.netCashFlow(income, expense);
        NetCashFlowResult result = new NetCashFlowResult(
            title(plan, first),
            AnalysisMetric.SUM,
            AnalysisUnit.CNY,
            null,
            new AnalysisResultPeriod(window, null),
            filters(first),
            resultWarnings,
            values);
        acc.results.add(result);
        if (!isZero(income) || incomeCount > 0 || !isZero(expense) || expenseCount > 0) {
            acc.withData++;
        }
        return acc.toOutcome();
    }

    private static String failedCashFlowLegMessage(NormalizedAnalysisQuery query) {
        if (query.transactionType() == TransactionType.INCOME) {
            return "收入查询失败";
        }
        if (query.transactionType() == TransactionType.EXPENSE) {
            return "支出查询失败";
        }
        return "查询失败";
    }

    private AnalysisResult baseResult(Long ledgerId, NormalizedAnalysisPlan plan, NormalizedAnalysisQuery query) {
        return switch (query.kind()) {
            case AGGREGATE -> toAggregate(plan, query, readService.aggregate(ledgerId, query));
            case BREAKDOWN -> toBreakdown(plan, query, readService.breakdown(ledgerId, query));
            case TREND -> toTrend(plan, query, readService.trend(ledgerId, query));
            case TRANSACTIONS -> toTransactions(plan, query, readService.transactions(ledgerId, query));
        };
    }

    private AnalysisResult averageByPeriodResult(Long ledgerId, NormalizedAnalysisPlan plan,
                                                 NormalizedAnalysisQuery query) {
        AnalysisTrendFact fact = readService.trend(ledgerId, query);
        DerivedMetricCalculator.AverageByPeriodValues values = calculator.averageByPeriod(fact.points());
        return new AverageByPeriodResult(
            title(plan, query),
            metric(query),
            unit(query),
            query.transactionType(),
            currentPeriod(query),
            filters(query),
            List.of(),
            values.points(),
            new AverageByPeriodResult.Values(values.average()));
    }

    private AggregateResult toAggregate(NormalizedAnalysisPlan plan, NormalizedAnalysisQuery query,
                                        AnalysisAggregateFact fact) {
        return new AggregateResult(
            title(plan, query),
            metric(query),
            unit(query),
            query.transactionType(),
            currentPeriod(query),
            filters(query),
            List.of(),
            new AggregateResult.Values(scale(fact.amount()), fact.count()));
    }

    private BreakdownResult toBreakdown(NormalizedAnalysisPlan plan, NormalizedAnalysisQuery query,
                                        AnalysisBreakdownFact fact) {
        DerivedMetricCalculator.BreakdownValues values = calculator.withShares(fact);
        return new BreakdownResult(
            title(plan, query),
            metric(query),
            unit(query),
            query.transactionType(),
            currentPeriod(query),
            filters(query),
            List.of(),
            values.rows(),
            values.associationShare());
    }

    private TrendResult toTrend(NormalizedAnalysisPlan plan, NormalizedAnalysisQuery query, AnalysisTrendFact fact) {
        return new TrendResult(
            title(plan, query),
            metric(query),
            unit(query),
            query.transactionType(),
            currentPeriod(query),
            filters(query),
            List.of(),
            fact.points());
    }

    private TransactionsResult toTransactions(NormalizedAnalysisPlan plan, NormalizedAnalysisQuery query,
                                              List<AnalysisTransactionFact> transactions) {
        List<AnalysisTransactionFact> rows = transactions == null ? List.of() : transactions;
        return new TransactionsResult(
            title(plan, query),
            metric(query),
            unit(query),
            query.transactionType(),
            currentPeriod(query),
            filters(query),
            List.of(),
            new TransactionsResult.Values(rows.size(), rows.size(), false),
            rows);
    }

    private static NormalizedAnalysisQuery withPeriod(NormalizedAnalysisQuery query, AnalysisDateRange period) {
        return new NormalizedAnalysisQuery(
            query.id(),
            query.kind(),
            query.metric(),
            query.transactionType(),
            query.periodSemantic(),
            period,
            query.filters(),
            query.dimension(),
            query.timeGrain(),
            query.sortField(),
            query.sortDirection(),
            query.limit(),
            query.title());
    }

    private static String title(NormalizedAnalysisPlan plan, NormalizedAnalysisQuery query) {
        if (query != null && query.title() != null && !query.title().isBlank()) {
            return query.title();
        }
        return plan.title();
    }

    private static AnalysisMetric metric(NormalizedAnalysisQuery query) {
        return query.metric() == null ? AnalysisMetric.SUM : query.metric();
    }

    private static AnalysisUnit unit(NormalizedAnalysisQuery query) {
        return metric(query) == AnalysisMetric.COUNT ? AnalysisUnit.COUNT : AnalysisUnit.CNY;
    }

    private static AnalysisFilters filters(NormalizedAnalysisQuery query) {
        return query.filters() == null ? AnalysisFilters.empty() : query.filters();
    }

    private static AnalysisResultPeriod currentPeriod(NormalizedAnalysisQuery query) {
        return new AnalysisResultPeriod(query.period(), null);
    }

    private static boolean isEmpty(AnalysisResult result) {
        if (result instanceof AggregateResult aggregate) {
            return aggregate.values().count() == 0 && isZero(aggregate.values().value());
        }
        if (result instanceof BreakdownResult breakdown) {
            return breakdown.rows().isEmpty();
        }
        if (result instanceof TrendResult trend) {
            return isEmptyTrend(trend.points());
        }
        if (result instanceof AverageByPeriodResult average) {
            return isEmptyTrend(average.points());
        }
        if (result instanceof TransactionsResult transactions) {
            return transactions.transactions().isEmpty();
        }
        if (result instanceof PeriodCompareResult compare) {
            return isZero(compare.values().current()) && isZero(compare.values().previous());
        }
        if (result instanceof NetCashFlowResult cashFlow) {
            return isZero(cashFlow.values().income()) && isZero(cashFlow.values().expense());
        }
        return false;
    }

    private static boolean isEmpty(AnalysisAggregateFact fact) {
        return fact.count() == 0 && isZero(fact.amount());
    }

    private static boolean isEmptyTrend(List<AnalysisTrendFact.Point> points) {
        if (points == null || points.isEmpty()) {
            return true;
        }
        long totalCount = points.stream().mapToLong(AnalysisTrendFact.Point::count).sum();
        boolean allZero = points.stream().allMatch(point -> isZero(point.value()));
        return allZero && totalCount == 0;
    }

    private static boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> QueryAttempt<T> attempt(Supplier<T> supplier, List<AnalysisWarning> warnings) {
        try {
            return QueryAttempt.ok(supplier.get());
        } catch (DataAccessException ex) {
            warnings.add(new AnalysisWarning("QUERY_FAILED", "查询失败"));
            return QueryAttempt.fail();
        }
    }

    private static final class ExecutionAcc {
        private final List<AnalysisResult> results = new ArrayList<>();
        private final List<AnalysisWarning> warnings = new ArrayList<>();
        private int succeeded;
        private int failed;
        private int withData;

        private AnalysisExecutionOutcome toOutcome() {
            AnalysisResponseStatus status;
            if (succeeded == 0 && failed > 0) {
                status = AnalysisResponseStatus.QUERY_FAILED;
            } else if (failed > 0) {
                status = AnalysisResponseStatus.PARTIAL_RESULT;
            } else if (withData == 0) {
                status = AnalysisResponseStatus.NO_DATA;
            } else {
                status = AnalysisResponseStatus.OK;
            }
            return new AnalysisExecutionOutcome(status, results, warnings);
        }
    }

    private static final class QueryAttempt<T> {
        private final T value;
        private final boolean failed;

        private QueryAttempt(T value, boolean failed) {
            this.value = value;
            this.failed = failed;
        }

        private static <T> QueryAttempt<T> ok(T value) {
            return new QueryAttempt<>(value, false);
        }

        private static <T> QueryAttempt<T> fail() {
            return new QueryAttempt<>(null, true);
        }
    }
}
