package com.liuqitech.accountingassistant.integration;

import com.liuqitech.accountingassistant.dto.CategoryStatisticsDto;
import com.liuqitech.accountingassistant.dto.StatisticsSummaryDto;
import com.liuqitech.accountingassistant.dto.TrendDataDto;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisAggregateFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisBreakdownFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.enums.AnalysisDimension;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisQueryKind;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.service.StatisticsService;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPeriodResolverImpl;
import com.liuqitech.accountingassistant.service.analysis.AnalysisReadService;
import com.liuqitech.accountingassistant.util.AppClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsAnalysisConsistencySqliteIntegrationTest {

    private static final String JDBC_URL = "jdbc:sqlite:file:analysis-stats-test?mode=memory&cache=shared";
    private static final long LEDGER_ID = 100L;

    private static Connection keepAlive;
    private static JdbcTemplate jdbcTemplate;
    private static AnalysisReadService readService;
    private static StatisticsService statisticsService;
    private static final QueryBuilder queryBuilder = new QueryBuilder();

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(JDBC_URL);
        keepAlive = dataSource.getConnection();
        ScriptUtils.executeSqlScript(keepAlive, new ClassPathResource("analysis/sqlite-fixture.sql"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        readService = new AnalysisReadService(jdbcTemplate, new AnalysisPeriodResolverImpl());
        statisticsService = new StatisticsService(null, null, readService);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    @Test
    void statisticsAndChatUseSameLedgerDateTypeTransferAndCategoryRules() {
        LocalDate today = AppClock.today();
        LocalDate[] range = new LocalDate[] {today.withDayOfYear(1), today};
        insertConsistencyFixtureRowsRelativeTo(today);

        StatisticsSummaryDto summary = statisticsService.getSummary(LEDGER_ID, "current_year");
        AnalysisAggregateFact income = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.INCOME, range[0], range[1]));
        AnalysisAggregateFact expense = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, range[0], range[1]));

        assertThat(summary.getTotalIncome()).isEqualByComparingTo(income.amount());
        assertThat(summary.getTotalExpense()).isEqualByComparingTo(expense.amount());
        assertThat(summary.getTransactionCount()).isEqualTo(income.count() + expense.count());
    }

    @Test
    void categoryStatisticsMapFromParentBreakdownFacts() {
        LocalDate today = AppClock.today();
        insertConsistencyFixtureRowsRelativeTo(today);
        LocalDate start = today.withDayOfYear(1);

        List<CategoryStatisticsDto> stats = statisticsService.getByCategory(
            LEDGER_ID, "current_year", TransactionType.EXPENSE);
        AnalysisBreakdownFact fact = readService.breakdown(LEDGER_ID,
            queryBuilder.breakdown(AnalysisDimension.PARENT_CATEGORY, TransactionType.EXPENSE, start, today));

        assertThat(stats).hasSize(fact.rows().size());
        for (int i = 0; i < stats.size(); i++) {
            CategoryStatisticsDto dto = stats.get(i);
            AnalysisBreakdownFact.Row row = fact.rows().get(i);
            assertThat(dto.getCategoryId()).isEqualTo(row.id());
            assertThat(dto.getCategoryName()).isNull();
            assertThat(dto.getParentCategoryName()).isEqualTo(row.label());
            assertThat(dto.getAmount()).isEqualByComparingTo(row.value());
            assertThat(dto.getTransactionCount()).isEqualTo(row.count());
            if (fact.denominator().compareTo(BigDecimal.ZERO) > 0) {
                assertThat(dto.getPercentage()).isEqualByComparingTo(
                    row.value().multiply(new BigDecimal("100")).divide(fact.denominator(), 2, java.math.RoundingMode.HALF_UP));
            }
        }
        assertThat(stats).extracting(CategoryStatisticsDto::getParentCategoryName)
            .doesNotContain("其他账本账户", "其他账本标签");
    }

    @Test
    void monthlyAndDailyTrendsShareNamedSeriesAndZeroBuckets() {
        LocalDate today = AppClock.today();
        insertConsistencyFixtureRowsRelativeTo(today);
        LocalDate monthStart = today.withDayOfMonth(1);

        List<TrendDataDto> monthly = statisticsService.getMonthlyTrend(LEDGER_ID, 3);
        LocalDate trendStart = today.minusMonths(2).withDayOfMonth(1);
        AnalysisTrendFact monthFact = readService.trend(LEDGER_ID,
            queryBuilder.trend(AnalysisTimeGrain.MONTH, null, trendStart, today));

        assertThat(monthly).hasSize(monthFact.points().size());
        for (int i = 0; i < monthly.size(); i++) {
            TrendDataDto dto = monthly.get(i);
            AnalysisTrendFact.Point point = monthFact.points().get(i);
            assertThat(dto.getMonth()).isEqualTo(point.period());
            assertThat(dto.getIncome()).isEqualByComparingTo(point.income());
            assertThat(dto.getExpense()).isEqualByComparingTo(point.expense());
            assertThat(dto.getBalance()).isEqualByComparingTo(point.income().subtract(point.expense()));
        }

        List<TrendDataDto> daily = statisticsService.getDailyTrend(LEDGER_ID, "current_month");
        AnalysisTrendFact dayFact = readService.trend(LEDGER_ID,
            queryBuilder.trend(AnalysisTimeGrain.DAY, null, monthStart, today));
        assertThat(daily).hasSize(dayFact.points().size());
        assertThat(daily).extracting(TrendDataDto::getMonth)
            .containsExactlyElementsOf(dayFact.points().stream().map(AnalysisTrendFact.Point::period).toList());
    }

    private void insertConsistencyFixtureRowsRelativeTo(LocalDate today) {
        String yearStart = today.withDayOfYear(1).toString();
        jdbcTemplate.update(
            "INSERT OR IGNORE INTO transactions (id, amount, type, description, original_text, category_id, category, "
                + "parent_category_id, parent_category_name, transaction_date, note, parsed_merchant, "
                + "ledger_id, account_id, deleted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            9001, new BigDecimal("88.00"), "INCOME", "一致性收入", "一致性收入",
            3, "工资", null, null, yearStart, null, "公司", LEDGER_ID, 1, null);
        jdbcTemplate.update(
            "INSERT OR IGNORE INTO transactions (id, amount, type, description, original_text, category_id, category, "
                + "parent_category_id, parent_category_name, transaction_date, note, parsed_merchant, "
                + "ledger_id, account_id, deleted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            9002, new BigDecimal("33.00"), "EXPENSE", "一致性支出", "一致性支出",
            2, "早餐", 1, "餐饮", yearStart, null, "包子铺", LEDGER_ID, 1, null);
        jdbcTemplate.update(
            "INSERT OR IGNORE INTO transactions (id, amount, type, description, original_text, category_id, category, "
                + "parent_category_id, parent_category_name, transaction_date, note, parsed_merchant, "
                + "ledger_id, account_id, deleted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            9003, new BigDecimal("12.00"), "TRANSFER", "一致性转账", "一致性转账",
            1, "餐饮", 1, "餐饮", yearStart, null, null, LEDGER_ID, 1, null);
        jdbcTemplate.update(
            "INSERT OR IGNORE INTO transactions (id, amount, type, description, original_text, category_id, category, "
                + "parent_category_id, parent_category_name, transaction_date, note, parsed_merchant, "
                + "ledger_id, account_id, deleted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            9004, new BigDecimal("99.00"), "EXPENSE", "一致性软删除", "一致性软删除",
            2, "早餐", 1, "餐饮", yearStart, null, null, LEDGER_ID, 1, today + " 00:00:00");
    }

    private static final class QueryBuilder {
        private NormalizedAnalysisQuery aggregate(TransactionType transactionType, LocalDate start, LocalDate end) {
            return new NormalizedAnalysisQuery(
                "agg", AnalysisQueryKind.AGGREGATE, AnalysisMetric.SUM, transactionType,
                AnalysisPeriodSpec.explicit(start, end), new AnalysisDateRange(start, end),
                AnalysisFilters.empty(), null, null, AnalysisSortField.AMOUNT, AnalysisSortDirection.DESC,
                50, "aggregate");
        }

        private NormalizedAnalysisQuery breakdown(AnalysisDimension dimension, TransactionType transactionType,
                                                  LocalDate start, LocalDate end) {
            return new NormalizedAnalysisQuery(
                "brk", AnalysisQueryKind.BREAKDOWN, AnalysisMetric.SUM, transactionType,
                AnalysisPeriodSpec.explicit(start, end), new AnalysisDateRange(start, end),
                AnalysisFilters.empty(), dimension, null, AnalysisSortField.AMOUNT, AnalysisSortDirection.DESC,
                50, "breakdown");
        }

        private NormalizedAnalysisQuery trend(AnalysisTimeGrain grain, TransactionType transactionType,
                                              LocalDate start, LocalDate end) {
            return new NormalizedAnalysisQuery(
                "trd", AnalysisQueryKind.TREND, AnalysisMetric.SUM, transactionType,
                AnalysisPeriodSpec.explicit(start, end), new AnalysisDateRange(start, end),
                AnalysisFilters.empty(), null, grain, AnalysisSortField.DATE, AnalysisSortDirection.ASC,
                366, "trend");
        }
    }
}
