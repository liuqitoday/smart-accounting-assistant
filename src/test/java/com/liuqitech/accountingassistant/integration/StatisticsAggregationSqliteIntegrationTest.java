package com.liuqitech.accountingassistant.integration;

import com.liuqitech.accountingassistant.dto.StatisticsSummaryDto;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StatisticsAggregationSqliteIntegrationTest {

    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private AnalysisReadService reads;
    private StatisticsService statistics;
    private long nextId;

    @BeforeEach
    void setUp() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:file:statistics-aggregation-" + UUID.randomUUID() + "?mode=memory&cache=shared");
        keepAlive = dataSource.getConnection();
        ScriptUtils.executeSqlScript(keepAlive, new ClassPathResource("analysis/sqlite-fixture.sql"));
        jdbc = spy(new JdbcTemplate(dataSource));
        jdbc.update("DELETE FROM transaction_tags");
        jdbc.update("DELETE FROM transactions");
        reads = new AnalysisReadService(jdbc, new AnalysisPeriodResolverImpl());
        statistics = new StatisticsService(null, null, reads);
        nextId = 1000;
    }

    @AfterEach
    void tearDown() throws SQLException {
        keepAlive.close();
    }

    @Test
    void summaryReadsCurrentAndPreviousPeriodsOnceAndPreservesBoundariesAndPercentages() {
        LocalDate today = AppClock.today();
        LocalDate start = today.withDayOfMonth(1);
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(ChronoUnit.DAYS.between(start, today));
        insert(start, "INCOME", "120.20", 100, false);
        insert(today, "EXPENSE", "30.10", 100, false);
        insert(previousStart, "INCOME", "60.10", 100, false);
        insert(previousEnd, "EXPENSE", "60.20", 100, false);
        insert(previousStart.minusDays(1), "EXPENSE", "9999", 100, false);
        insert(today.plusDays(1), "INCOME", "9999", 100, false);
        insert(today, "TRANSFER", "9999", 100, false);
        insert(today, "EXPENSE", "9999", 100, true);
        insert(today, "INCOME", "9999", 200, false);
        clearInvocations(jdbc);

        StatisticsSummaryDto summary = statistics.getSummary(100L, "current_month");

        assertThat(summary.getTotalIncome()).isEqualByComparingTo("120.20");
        assertThat(summary.getTotalExpense()).isEqualByComparingTo("30.10");
        assertThat(summary.getBalance()).isEqualByComparingTo("90.10");
        assertThat(summary.getTransactionCount()).isEqualTo(2);
        assertThat(summary.getPrevTotalIncome()).isEqualByComparingTo("60.10");
        assertThat(summary.getPrevTotalExpense()).isEqualByComparingTo("60.20");
        assertThat(summary.getIncomeChangePct()).isEqualTo(100.0);
        assertThat(summary.getExpenseChangePct()).isEqualTo(-50.0);
        assertThat(summary.getPeriodStart()).isEqualTo(start.toString());
        assertThat(summary.getPeriodEnd()).isEqualTo(today.toString());
        verify(jdbc, times(1)).query(anyString(), any(Object[].class), any(RowMapper.class));
    }

    @Test
    void emptySummaryKeepsZeroAmountsAndUndefinedPercentageChanges() {
        StatisticsSummaryDto summary = statistics.getSummary(100L, "current_year");
        assertThat(summary.getTotalIncome()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalExpense()).isEqualByComparingTo("0.00");
        assertThat(summary.getPrevTotalIncome()).isEqualByComparingTo("0.00");
        assertThat(summary.getPrevTotalExpense()).isEqualByComparingTo("0.00");
        assertThat(summary.getTransactionCount()).isZero();
        assertThat(summary.getIncomeChangePct()).isNull();
        assertThat(summary.getExpenseChangePct()).isNull();
    }

    @Test
    void groupedTrendPreservesRoundedSumsCountsAndWeightedAveragesForEveryTransactionType() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        insert(date, "INCOME", "100.10", 100, false);
        insert(date, "INCOME", "200.20", 100, false);
        insert(date, "EXPENSE", "10.005", 100, false);
        insert(date, "EXPENSE", "20.004", 100, false);
        insert(date, "EXPENSE", "30.10", 100, false);
        insert(date, "EXPENSE", "1.005", 100, false);
        insert(date, "EXPENSE", "2.675", 100, false);
        insert(date, "TRANSFER", "9999", 100, false);
        insert(date, "EXPENSE", "9999", 100, true);
        insert(date, "INCOME", "9999", 200, false);

        for (AnalysisMetric metric : AnalysisMetric.values()) {
            for (TransactionType type : new TransactionType[] {null, TransactionType.INCOME, TransactionType.EXPENSE}) {
                AnalysisTrendFact fact = trend(AnalysisTimeGrain.MONTH, metric, type,
                    date.withDayOfMonth(1), date.plusMonths(1).withDayOfMonth(28), AnalysisFilters.empty());
                AnalysisTrendFact.Point point = fact.points().get(0);
                BigDecimal income = type == TransactionType.EXPENSE ? BigDecimal.ZERO : new BigDecimal("300.30");
                BigDecimal expense = type == TransactionType.INCOME ? BigDecimal.ZERO : new BigDecimal("63.80");
                long count = type == null ? 7 : type == TransactionType.INCOME ? 2 : 5;
                BigDecimal expected = switch (metric) {
                    case COUNT -> BigDecimal.valueOf(count);
                    case SUM -> income.add(expense);
                    case AVG -> income.add(expense).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                };
                assertThat(point.income()).isEqualByComparingTo(income);
                assertThat(point.expense()).isEqualByComparingTo(expense);
                assertThat(point.value()).isEqualByComparingTo(expected);
                assertThat(point.count()).isEqualTo(count);
                assertThat(fact.points().get(1).value()).isEqualByComparingTo("0.00");
                assertThat(fact.points().get(1).count()).isZero();
            }
        }
    }

    @Test
    void groupedTrendPreservesCentsWhenTheTotalExceedsFloatingPointPrecision() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        for (int i = 0; i < 17; i++) insert(date, "EXPENSE", "9999999999999.01", 100, false);
        insert(date, "EXPENSE", "1.15", 100, false);
        insert(date, "EXPENSE", "1.1", 100, false);

        AnalysisTrendFact fact = trend(AnalysisTimeGrain.MONTH, AnalysisMetric.SUM, TransactionType.EXPENSE,
            date.withDayOfMonth(1), date.withDayOfMonth(31), AnalysisFilters.empty());

        // REAL SUM used to lose a cent even though every input fits NUMERIC(15,2).
        assertThat(fact.points().get(0).expense()).isEqualByComparingTo("169999999999985.42");
        assertThat(fact.points().get(0).value()).isEqualByComparingTo("169999999999985.42");
        assertThat(fact.points().get(0).count()).isEqualTo(19);
    }

    @Test
    void integerOverflowFallsBackToExactStreamingAggregationWithoutLosingFilters() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        jdbc.update("WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000) "
                + "INSERT INTO transactions (id,amount,type,description,original_text,category_id,category,"
                + "parent_category_id,parent_category_name,transaction_date,parsed_merchant,ledger_id,account_id) "
                + "SELECT n + 10000, CAST(? AS NUMERIC), 'EXPENSE', 'test_', 'test_', 2, '早餐', 1, '餐饮', ?, 'shop%', 100, 1 FROM seq",
            "9999999999999.01", date.toString());
        insert(date, "EXPENSE", "10", 200, false);
        insert(date, "EXPENSE", "10", 100, true);
        insert(date.plusMonths(1), "EXPENSE", "10", 100, false);

        AnalysisTrendFact fact = trend(AnalysisTimeGrain.MONTH, AnalysisMetric.AVG, TransactionType.EXPENSE,
            date.withDayOfMonth(1), date.withDayOfMonth(31), AnalysisFilters.empty());

        assertThat(fact.points().get(0).expense()).isEqualByComparingTo("99999999999990100.00");
        assertThat(fact.points().get(0).value()).isEqualByComparingTo("9999999999999.01");
        assertThat(fact.points().get(0).count()).isEqualTo(10000);
    }

    @Test
    void weekBucketsCrossYearAndHonorPartialRange() {
        insert(LocalDate.of(2025, 12, 28), "EXPENSE", "999", 100, false);
        insert(LocalDate.of(2025, 12, 29), "EXPENSE", "2", 100, false);
        insert(LocalDate.of(2026, 1, 4), "EXPENSE", "3", 100, false);
        insert(LocalDate.of(2026, 1, 5), "INCOME", "7", 100, false);
        AnalysisTrendFact fact = trend(AnalysisTimeGrain.WEEK, AnalysisMetric.SUM, null,
            LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 12), AnalysisFilters.empty());
        assertThat(fact.points()).extracting(AnalysisTrendFact.Point::period)
            .containsExactly("2025-12-29", "2026-01-05", "2026-01-12");
        assertThat(fact.points()).extracting(AnalysisTrendFact.Point::value)
            .containsExactly(new BigDecimal("5.00"), new BigDecimal("7.00"), new BigDecimal("0.00"));
    }

    @Test
    void quarterAndYearBucketsIncludeEmptyPeriodsAndKeepBoundaryRowsSeparate() {
        insert(LocalDate.of(2025, 12, 31), "EXPENSE", "2", 100, false);
        insert(LocalDate.of(2026, 1, 1), "EXPENSE", "3", 100, false);
        insert(LocalDate.of(2026, 3, 31), "EXPENSE", "4", 100, false);
        insert(LocalDate.of(2026, 4, 1), "INCOME", "5", 100, false);
        LocalDate start = LocalDate.of(2025, 12, 30);
        LocalDate end = LocalDate.of(2027, 1, 2);
        AnalysisTrendFact quarters = trend(AnalysisTimeGrain.QUARTER, AnalysisMetric.SUM, null,
            start, end, AnalysisFilters.empty());
        assertThat(quarters.points()).extracting(AnalysisTrendFact.Point::period)
            .containsExactly("2025-Q4", "2026-Q1", "2026-Q2", "2026-Q3", "2026-Q4", "2027-Q1");
        assertThat(quarters.points()).extracting(AnalysisTrendFact.Point::value)
            .containsExactly(new BigDecimal("2.00"), new BigDecimal("7.00"), new BigDecimal("5.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));
        AnalysisTrendFact years = trend(AnalysisTimeGrain.YEAR, AnalysisMetric.COUNT, null,
            start, end, AnalysisFilters.empty());
        assertThat(years.points()).extracting(AnalysisTrendFact.Point::period)
            .containsExactly("2025", "2026", "2027");
        assertThat(years.points()).extracting(AnalysisTrendFact.Point::count).containsExactly(1L, 3L, 0L);
    }

    @Test
    void dailyLeapDayAndCombinedFiltersDoNotDuplicateMultiTagTransactions() {
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        long wanted = insert(leapDay, "EXPENSE", "12.34", 100, false);
        long foreignTagged = insert(leapDay, "EXPENSE", "12.34", 100, false);
        insert(leapDay, "EXPENSE", "99", 100, false);
        jdbc.update("INSERT INTO transaction_tags VALUES (?,1),(?,2),(?,3)", wanted, wanted, foreignTagged);
        AnalysisFilters filters = new AnalysisFilters(1L, 1L, List.of(1L, 2L, 3L),
            "shop%", "test_", new BigDecimal("10"), new BigDecimal("15"));
        AnalysisTrendFact fact = trend(AnalysisTimeGrain.DAY, AnalysisMetric.AVG, TransactionType.EXPENSE,
            leapDay.minusDays(1), leapDay.plusDays(1), filters);
        assertThat(fact.points()).extracting(AnalysisTrendFact.Point::period)
            .containsExactly("2024-02-28", "2024-02-29", "2024-03-01");
        assertThat(fact.points()).extracting(AnalysisTrendFact.Point::count).containsExactly(0L, 1L, 0L);
        assertThat(fact.points().get(1).expense()).isEqualByComparingTo("12.34");
        assertThat(fact.points().get(1).value()).isEqualByComparingTo("12.34");
    }

    private AnalysisTrendFact trend(AnalysisTimeGrain grain, AnalysisMetric metric, TransactionType type,
                                    LocalDate start, LocalDate end, AnalysisFilters filters) {
        return reads.trend(100L, new NormalizedAnalysisQuery(
            "trend", AnalysisQueryKind.TREND, metric, type, AnalysisPeriodSpec.explicit(start, end),
            new AnalysisDateRange(start, end), filters, null, grain,
            AnalysisSortField.DATE, AnalysisSortDirection.ASC, 366, "trend"));
    }

    private long insert(LocalDate date, String type, String amount, long ledger, boolean deleted) {
        long id = nextId++;
        jdbc.update("INSERT INTO transactions (id,amount,type,description,original_text,category_id,category,"
                + "parent_category_id,parent_category_name,transaction_date,parsed_merchant,ledger_id,account_id,deleted_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id, new BigDecimal(amount), type, "test_ item", "test_ item", 2L, "早餐", 1L, "餐饮",
            date.toString(), "shop% branch", ledger, 1L, deleted ? "2026-01-01" : null);
        return id;
    }
}
