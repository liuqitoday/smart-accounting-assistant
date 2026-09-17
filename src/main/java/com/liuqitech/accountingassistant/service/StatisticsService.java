package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.CategoryStatisticsDto;
import com.liuqitech.accountingassistant.dto.StatisticsSummaryDto;
import com.liuqitech.accountingassistant.dto.TransactionDto;
import com.liuqitech.accountingassistant.dto.TrendDataDto;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisAggregateFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisBreakdownFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.enums.AnalysisDimension;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisQueryKind;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.mapper.TransactionMapper;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.service.analysis.AnalysisReadService;
import com.liuqitech.accountingassistant.util.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统计服务类
 */
@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final AnalysisReadService analysisReadService;

    public StatisticsService(TransactionRepository transactionRepository,
                             TransactionMapper transactionMapper,
                             AnalysisReadService analysisReadService) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
        this.analysisReadService = analysisReadService;
    }

    /**
     * 获取汇总数据（含上一同期环比）
     */
    public StatisticsSummaryDto getSummary(Long ledgerId, String period) {
        LocalDate[] dateRange = calculateDateRange(period);
        LocalDate startDate = dateRange[0];
        LocalDate endDate = dateRange[1];

        AnalysisAggregateFact income = analysisReadService.aggregate(
            ledgerId, aggregateQuery(TransactionType.INCOME, startDate, endDate));
        AnalysisAggregateFact expense = analysisReadService.aggregate(
            ledgerId, aggregateQuery(TransactionType.EXPENSE, startDate, endDate));
        BigDecimal totalIncome = income.amount();
        BigDecimal totalExpense = expense.amount();
        Long transactionCount = income.count() + expense.count();

        long durationDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate prevEnd = startDate.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(durationDays - 1);
        AnalysisAggregateFact prevIncomeFact = analysisReadService.aggregate(
            ledgerId, aggregateQuery(TransactionType.INCOME, prevStart, prevEnd));
        AnalysisAggregateFact prevExpenseFact = analysisReadService.aggregate(
            ledgerId, aggregateQuery(TransactionType.EXPENSE, prevStart, prevEnd));
        BigDecimal prevIncome = prevIncomeFact.amount();
        BigDecimal prevExpense = prevExpenseFact.amount();

        StatisticsSummaryDto summary = new StatisticsSummaryDto();
        summary.setTotalIncome(totalIncome);
        summary.setTotalExpense(totalExpense);
        summary.setBalance(summary.getTotalIncome().subtract(summary.getTotalExpense()));
        summary.setTransactionCount(transactionCount);
        summary.setPeriodStart(startDate.format(DATE_FORMATTER));
        summary.setPeriodEnd(endDate.format(DATE_FORMATTER));
        summary.setPrevTotalIncome(prevIncome);
        summary.setPrevTotalExpense(prevExpense);
        summary.setIncomeChangePct(changePct(totalIncome, prevIncome));
        summary.setExpenseChangePct(changePct(totalExpense, prevExpense));

        return summary;
    }

    /**
     * 获取分类统计（按 type：INCOME/EXPENSE）
     */
    public List<CategoryStatisticsDto> getByCategory(Long ledgerId, String period, TransactionType type) {
        LocalDate[] dateRange = calculateDateRange(period);
        AnalysisBreakdownFact fact = analysisReadService.breakdown(
            ledgerId, breakdownQuery(type, dateRange[0], dateRange[1]));
        List<CategoryStatisticsDto> statistics = new ArrayList<>();
        BigDecimal denominator = fact.denominator() == null ? BigDecimal.ZERO : fact.denominator();
        for (AnalysisBreakdownFact.Row row : fact.rows()) {
            CategoryStatisticsDto dto = new CategoryStatisticsDto();
            dto.setCategoryId(row.id());
            dto.setCategoryName(null);
            dto.setParentCategoryName(row.label());
            dto.setAmount(row.value());
            dto.setTransactionCount(row.count());
            if (denominator.compareTo(BigDecimal.ZERO) > 0) {
                dto.setPercentage(row.value()
                    .multiply(new BigDecimal("100"))
                    .divide(denominator, 2, RoundingMode.HALF_UP));
            } else {
                dto.setPercentage(BigDecimal.ZERO);
            }
            statistics.add(dto);
        }
        return statistics;
    }

    /**
     * 获取月度趋势
     */
    public List<TrendDataDto> getMonthlyTrend(Long ledgerId, int months) {
        LocalDate endDate = AppClock.today();
        LocalDate startDate = endDate.minusMonths(months - 1).withDayOfMonth(1);
        return toTrendDtos(analysisReadService.trend(
            ledgerId, trendQuery(AnalysisTimeGrain.MONTH, startDate, endDate)));
    }

    /**
     * 获取日趋势（按日聚合，供「本月」日粒度趋势图）
     */
    public List<TrendDataDto> getDailyTrend(Long ledgerId, String period) {
        LocalDate[] dateRange = calculateDateRange(period);
        return toTrendDtos(analysisReadService.trend(
            ledgerId, trendQuery(AnalysisTimeGrain.DAY, dateRange[0], dateRange[1])));
    }

    /**
     * 获取大额支出排行（指定日期范围内金额最大的 5 笔支出）
     */
    public List<TransactionDto> getTopExpenses(Long ledgerId, String period) {
        LocalDate[] dateRange = calculateDateRange(period);
        List<Transaction> transactions = transactionRepository.findTop5ByLedgerIdAndTypeAndTransactionDateBetweenOrderByAmountDesc(
                ledgerId, TransactionType.EXPENSE.name(),
                dateRange[0].format(DATE_FORMATTER),
                dateRange[1].format(DATE_FORMATTER));
        return toDtoList(transactions);
    }

    /**
     * 获取最近交易
     */
    public List<TransactionDto> getRecentTransactions(Long ledgerId) {
        return toDtoList(transactionRepository.findTop5ByLedgerIdOrderByTransactionDateDescIdDesc(ledgerId));
    }

    /**
     * 根据时间段计算日期范围（按业务时区 Asia/Shanghai 取"今天"）
     */
    public LocalDate[] calculateDateRange(String period) {
        LocalDate now = AppClock.today();
        LocalDate startDate;
        LocalDate endDate = now;

        switch (period != null ? period : "current_month") {
            case "last_3_months":
                startDate = now.minusMonths(3).withDayOfMonth(1);
                break;
            case "last_6_months":
                startDate = now.minusMonths(6).withDayOfMonth(1);
                break;
            case "current_year":
                startDate = now.withMonth(1).withDayOfMonth(1);
                break;
            case "current_month":
            default:
                startDate = now.withDayOfMonth(1);
                break;
        }

        return new LocalDate[]{startDate, endDate};
    }

    /**
     * 环比百分比：(current - previous) / previous * 100；previous 为 0 时返回 null
     */
    private Double changePct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(new BigDecimal("100"))
                .divide(previous, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<TransactionDto> toDtoList(List<Transaction> transactions) {
        return transactions.stream()
                .map(transactionMapper::toDto)
                .collect(Collectors.toList());
    }

    private List<TrendDataDto> toTrendDtos(AnalysisTrendFact fact) {
        List<TrendDataDto> trendData = new ArrayList<>();
        for (AnalysisTrendFact.Point point : fact.points()) {
            trendData.add(new TrendDataDto(point.period(), point.income(), point.expense()));
        }
        return trendData;
    }

    private NormalizedAnalysisQuery aggregateQuery(TransactionType type, LocalDate start, LocalDate end) {
        return new NormalizedAnalysisQuery(
            "stats-agg", AnalysisQueryKind.AGGREGATE, AnalysisMetric.SUM, type,
            AnalysisPeriodSpec.explicit(start, end), new AnalysisDateRange(start, end),
            AnalysisFilters.empty(), null, null, AnalysisSortField.AMOUNT, AnalysisSortDirection.DESC,
            50, "stats-agg");
    }

    private NormalizedAnalysisQuery breakdownQuery(TransactionType type, LocalDate start, LocalDate end) {
        return new NormalizedAnalysisQuery(
            "stats-brk", AnalysisQueryKind.BREAKDOWN, AnalysisMetric.SUM, type,
            AnalysisPeriodSpec.explicit(start, end), new AnalysisDateRange(start, end),
            AnalysisFilters.empty(), AnalysisDimension.PARENT_CATEGORY, null,
            AnalysisSortField.AMOUNT, AnalysisSortDirection.DESC, 50, "stats-brk");
    }

    private NormalizedAnalysisQuery trendQuery(AnalysisTimeGrain grain, LocalDate start, LocalDate end) {
        return new NormalizedAnalysisQuery(
            "stats-trd", AnalysisQueryKind.TREND, AnalysisMetric.SUM, null,
            AnalysisPeriodSpec.explicit(start, end), new AnalysisDateRange(start, end),
            AnalysisFilters.empty(), null, grain, AnalysisSortField.DATE, AnalysisSortDirection.ASC,
            366, "stats-trd");
    }
}
