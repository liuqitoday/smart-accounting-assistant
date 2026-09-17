package com.liuqitech.accountingassistant.integration;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisAggregateFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisBreakdownFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTransactionFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.enums.AnalysisDimension;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisQueryKind;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPeriodResolverImpl;
import com.liuqitech.accountingassistant.service.analysis.AnalysisReadService;
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

class AnalysisReadServiceSqliteIntegrationTest {

    private static final String JDBC_URL = "jdbc:sqlite:file:analysis-read-test?mode=memory&cache=shared";
    private static final long LEDGER_ID = 100L;
    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate MAR_31 = LocalDate.of(2026, 3, 31);

    private static Connection keepAlive;
    private static AnalysisReadService readService;
    private static final QueryBuilder queryBuilder = new QueryBuilder();

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(JDBC_URL);
        keepAlive = dataSource.getConnection();
        ScriptUtils.executeSqlScript(keepAlive, new ClassPathResource("analysis/sqlite-fixture.sql"));
        readService = new AnalysisReadService(new JdbcTemplate(dataSource), new AnalysisPeriodResolverImpl());
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    @Test
    void breakdownSupportsParentAndChildCategoryAccountMerchantAndNonMutualTags() {
        NormalizedAnalysisQuery query = queryBuilder.breakdown(AnalysisDimension.PARENT_CATEGORY,
            TransactionType.EXPENSE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        AnalysisBreakdownFact fact = readService.breakdown(LEDGER_ID, query);

        assertThat(fact.rows()).extracting(AnalysisBreakdownFact.Row::label)
            .contains("生活", "餐饮");
        assertThat(fact.associationShare()).isFalse();
        assertThat(fact.rows()).allSatisfy(row -> assertThat(row.parentLabel()).isNull());

        AnalysisBreakdownFact child = readService.breakdown(LEDGER_ID,
            queryBuilder.breakdown(AnalysisDimension.CHILD_CATEGORY, TransactionType.EXPENSE, JAN_1, MAR_31));
        assertThat(child.rows()).extracting(AnalysisBreakdownFact.Row::label)
            .contains("早餐", "日用品", "公交");
        assertThat(child.rows()).filteredOn(row -> "早餐".equals(row.label()))
            .extracting(AnalysisBreakdownFact.Row::parentLabel)
            .containsOnly("餐饮");
        assertThat(child.associationShare()).isFalse();

        AnalysisBreakdownFact account = readService.breakdown(LEDGER_ID,
            queryBuilder.breakdown(AnalysisDimension.ACCOUNT, TransactionType.EXPENSE, JAN_1, MAR_31));
        assertThat(account.rows()).extracting(AnalysisBreakdownFact.Row::label)
            .contains("支付宝")
            .doesNotContain("其他账本账户");
        assertThat(account.associationShare()).isFalse();

        AnalysisBreakdownFact merchant = readService.breakdown(LEDGER_ID,
            queryBuilder.breakdown(AnalysisDimension.MERCHANT, TransactionType.EXPENSE, JAN_1, MAR_31));
        assertThat(merchant.rows()).extracting(AnalysisBreakdownFact.Row::label).contains("包子铺");
        assertThat(merchant.rows()).extracting(AnalysisBreakdownFact.Row::id).containsOnly((Long) null);
        assertThat(merchant.associationShare()).isFalse();

        AnalysisBreakdownFact tags = readService.breakdown(LEDGER_ID,
            queryBuilder.breakdown(AnalysisDimension.TAG, TransactionType.EXPENSE, JAN_1, MAR_31));
        assertThat(tags.rows()).extracting(AnalysisBreakdownFact.Row::label)
            .contains("旅行", "工作")
            .doesNotContain("其他账本标签");
        assertThat(tags.associationShare()).isTrue();
    }

    @Test
    void dailyAndMonthlyTrendContainsZeroBucketsAndNamedTransactions() {
        NormalizedAnalysisQuery query = queryBuilder.trend(AnalysisTimeGrain.MONTH,
            TransactionType.EXPENSE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        AnalysisTrendFact fact = readService.trend(LEDGER_ID, query);

        assertThat(fact.points()).extracting(AnalysisTrendFact.Point::period)
            .containsExactly("2026-01", "2026-02", "2026-03");
        assertThat(fact.points().get(1).value()).isEqualByComparingTo("0.00");
        assertThat(readService.transactions(LEDGER_ID, queryBuilder.transactions(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))).get(0).account()).isEqualTo("支付宝");
    }

    @Test
    void everyJoinRejectsOtherLedgerResources() {
        NormalizedAnalysisQuery query = queryBuilder.breakdown(AnalysisDimension.ACCOUNT,
            TransactionType.EXPENSE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(readService.breakdown(LEDGER_ID, query).rows())
            .extracting(AnalysisBreakdownFact.Row::label)
            .doesNotContain("其他账本账户", "其他账本标签");

        assertThat(readService.breakdown(LEDGER_ID,
            queryBuilder.breakdown(AnalysisDimension.TAG, TransactionType.EXPENSE, JAN_1, MAR_31)).rows())
            .extracting(AnalysisBreakdownFact.Row::label)
            .doesNotContain("其他账本账户", "其他账本标签");
    }

    @Test
    void topNRowsDoNotBecomeBreakdownDenominator() {
        NormalizedAnalysisQuery query = queryBuilder.breakdown(AnalysisDimension.PARENT_CATEGORY,
            TransactionType.EXPENSE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), 2);

        AnalysisBreakdownFact fact = readService.breakdown(LEDGER_ID, query);

        assertThat(fact.rows()).hasSize(2);
        assertThat(fact.denominator()).isEqualByComparingTo("200.00");
        assertThat(fact.rows()).extracting(AnalysisBreakdownFact.Row::label)
            .containsExactly("生活", "交通");
    }

    @Test
    void transferExcludedByDefaultAndExplicitIncomeExpenseWork() {
        AnalysisAggregateFact all = readService.aggregate(LEDGER_ID, queryBuilder.aggregate(null, JAN_1, MAR_31));
        AnalysisAggregateFact income = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.INCOME, JAN_1, MAR_31));
        AnalysisAggregateFact expense = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, JAN_1, MAR_31));

        assertThat(income.amount()).isEqualByComparingTo("5000.00");
        assertThat(income.count()).isEqualTo(1L);
        assertThat(expense.amount()).isEqualByComparingTo("200.00");
        assertThat(all.amount()).isEqualByComparingTo("5200.00");
        assertThat(all.count()).isEqualTo(income.count() + expense.count());
    }

    @Test
    void parentCategoryBlankDoesNotBecomeUncategorizedWhenCategorySet() {
        AnalysisBreakdownFact fact = readService.breakdown(LEDGER_ID,
            queryBuilder.breakdown(AnalysisDimension.PARENT_CATEGORY, TransactionType.EXPENSE, JAN_1, MAR_31));

        assertThat(fact.rows()).extracting(AnalysisBreakdownFact.Row::label)
            .contains("零食", "未分类")
            .doesNotContain("");
        assertThat(fact.rows()).filteredOn(row -> "零食".equals(row.label()))
            .first()
            .extracting(AnalysisBreakdownFact.Row::value)
            .satisfies(value -> assertThat((BigDecimal) value).isEqualByComparingTo("20.00"));
    }

    @Test
    void tagDimensionRepeatsMultiTagTransactionAndMarksAssociationShare() {
        AnalysisBreakdownFact fact = readService.breakdown(LEDGER_ID,
            queryBuilder.breakdown(AnalysisDimension.TAG, TransactionType.EXPENSE, JAN_1, MAR_31));

        assertThat(fact.associationShare()).isTrue();
        assertThat(fact.rows()).extracting(AnalysisBreakdownFact.Row::label)
            .containsExactlyInAnyOrder("旅行", "工作");
        assertThat(fact.rows()).allSatisfy(row ->
            assertThat(row.value()).isEqualByComparingTo("10.00"));
        assertThat(fact.denominator()).isEqualByComparingTo("20.00");
    }

    @Test
    void transactionFactContainsNamedFields() {
        List<AnalysisTransactionFact> facts = readService.transactions(LEDGER_ID,
            queryBuilder.transactions(JAN_1, MAR_31));
        AnalysisTransactionFact breakfast = facts.stream()
            .filter(row -> Long.valueOf(1L).equals(row.id()))
            .findFirst()
            .orElseThrow();

        assertThat(breakfast.amount()).isEqualByComparingTo("10.00");
        assertThat(breakfast.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(breakfast.date()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(breakfast.description()).isEqualTo("早餐包子");
        assertThat(breakfast.category()).isEqualTo("早餐");
        assertThat(breakfast.parentCategory()).isEqualTo("餐饮");
        assertThat(breakfast.account()).isEqualTo("支付宝");
        assertThat(breakfast.merchant()).isEqualTo("包子铺");
    }

    @Test
    void emptyAggregateReturnsZeroAmountAndCount() {
        AnalysisAggregateFact fact = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)));

        assertThat(fact.amount()).isEqualByComparingTo("0.00");
        assertThat(fact.count()).isEqualTo(0L);
    }

    @Test
    void weekTrendUsesMondayPartialBucketsAndFillsZeros() {
        AnalysisTrendFact fact = readService.trend(LEDGER_ID,
            queryBuilder.trend(AnalysisTimeGrain.WEEK, TransactionType.EXPENSE,
                LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 25)));

        assertThat(fact.points()).extracting(AnalysisTrendFact.Point::period)
            .containsExactly("2026-01-12", "2026-01-19");
        assertThat(fact.points().get(0).value()).isEqualByComparingTo("40.00");
        assertThat(fact.points().get(1).value()).isEqualByComparingTo("0.00");
        assertThat(fact.points().get(1).count()).isEqualTo(0L);
        assertThat(fact.points()).allSatisfy(point -> {
            assertThat(point.income()).isEqualByComparingTo("0.00");
            assertThat(point.expense()).isEqualByComparingTo(point.value());
        });
    }

    @Test
    void foreignTagIdFilterCannotBypassLedgerIsolation() {
        AnalysisFilters filters = filters(null, null, List.of(3L), null, null, null, null);

        AnalysisAggregateFact fact = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, JAN_1, MAR_31, filters));
        assertThat(fact.amount()).isEqualByComparingTo("0.00");
        assertThat(fact.count()).isEqualTo(0L);
        assertThat(readService.transactions(LEDGER_ID, queryBuilder.transactions(JAN_1, MAR_31, filters)))
            .isEmpty();
    }

    @Test
    void currentLedgerAndSystemTagIdsSelectMultiTagExpenseOnly() {
        for (List<Long> tagIds : List.of(List.of(1L), List.of(2L), List.of(1L, 2L))) {
            AnalysisFilters filters = filters(null, null, tagIds, null, null, null, null);
            AnalysisAggregateFact fact = readService.aggregate(LEDGER_ID,
                queryBuilder.aggregate(TransactionType.EXPENSE, JAN_1, MAR_31, filters));
            List<AnalysisTransactionFact> rows = readService.transactions(LEDGER_ID,
                queryBuilder.transactions(JAN_1, MAR_31, filters));

            assertThat(fact.amount()).isEqualByComparingTo("10.00");
            assertThat(fact.count()).isEqualTo(1L);
            assertThat(rows).extracting(AnalysisTransactionFact::id).containsExactly(9L);
            assertThat(rows).extracting(AnalysisTransactionFact::description).containsExactly("多标签支出");
            assertThat(rows).extracting(AnalysisTransactionFact::id)
                .doesNotContain(10L, 11L, 12L, 13L);
        }
    }

    @Test
    void accountIdFilterMatchesTransactionAccountId() {
        AnalysisFilters alipay = filters(null, 1L, List.of(), null, null, null, null);
        AnalysisAggregateFact alipayFact = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, JAN_1, MAR_31, alipay));
        List<AnalysisTransactionFact> alipayRows = readService.transactions(LEDGER_ID,
            queryBuilder.transactions(JAN_1, MAR_31, alipay));

        assertThat(alipayFact.amount()).isEqualByComparingTo("185.00");
        assertThat(alipayRows).extracting(AnalysisTransactionFact::account)
            .containsOnly("支付宝");
        assertThat(alipayRows).extracting(AnalysisTransactionFact::id).doesNotContain(8L);

        AnalysisFilters foreignAccount = filters(null, 2L, List.of(), null, null, null, null);
        AnalysisAggregateFact foreignFact = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, JAN_1, MAR_31, foreignAccount));
        List<AnalysisTransactionFact> foreignRows = readService.transactions(LEDGER_ID,
            queryBuilder.transactions(JAN_1, MAR_31, foreignAccount));

        assertThat(foreignFact.amount()).isEqualByComparingTo("5.00");
        assertThat(foreignFact.count()).isEqualTo(1L);
        assertThat(foreignRows).extracting(AnalysisTransactionFact::id).containsExactly(8L);
        assertThat(foreignRows).extracting(AnalysisTransactionFact::id).doesNotContain(11L, 12L, 13L);
        assertThat(foreignRows).allSatisfy(row -> assertThat(row.account()).isNull());
    }

    @Test
    void merchantKeywordAmountAndCategoryFiltersMatchSpecifiedColumns() {
        AnalysisFilters merchant = filters(null, null, List.of(), "包子铺", null, null, null);
        AnalysisAggregateFact merchantFact = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, JAN_1, MAR_31, merchant));
        assertThat(merchantFact.amount()).isEqualByComparingTo("20.00");
        assertThat(merchantFact.count()).isEqualTo(2L);
        assertThat(readService.transactions(LEDGER_ID, queryBuilder.transactions(JAN_1, MAR_31, merchant)))
            .extracting(AnalysisTransactionFact::merchant)
            .containsOnly("包子铺");

        AnalysisFilters keyword = filters(null, null, List.of(), null, "包子", null, null);
        List<AnalysisTransactionFact> keywordRows = readService.transactions(LEDGER_ID,
            queryBuilder.transactions(JAN_1, MAR_31, keyword));
        assertThat(keywordRows).extracting(AnalysisTransactionFact::id).containsExactly(1L);
        assertThat(keywordRows).extracting(AnalysisTransactionFact::description).containsExactly("早餐包子");

        AnalysisFilters amountRange = filters(null, null, List.of(), null, null,
            new BigDecimal("20.00"), new BigDecimal("50.00"));
        AnalysisAggregateFact rangeFact = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, JAN_1, MAR_31, amountRange));
        assertThat(rangeFact.amount()).isEqualByComparingTo("70.00");
        assertThat(rangeFact.count()).isEqualTo(2L);
        assertThat(readService.transactions(LEDGER_ID, queryBuilder.transactions(JAN_1, MAR_31, amountRange)))
            .extracting(AnalysisTransactionFact::id)
            .containsExactlyInAnyOrder(6L, 15L);

        AnalysisFilters parentCategory = filters(1L, null, List.of(), null, null, null, null);
        AnalysisAggregateFact parentFact = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, JAN_1, MAR_31, parentCategory));
        assertThat(parentFact.amount()).isEqualByComparingTo("40.00");
        assertThat(parentFact.count()).isEqualTo(5L);

        AnalysisFilters childCategory = filters(5L, null, List.of(), null, null, null, null);
        AnalysisAggregateFact childFact = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, JAN_1, MAR_31, childCategory));
        assertThat(childFact.amount()).isEqualByComparingTo("80.00");
        assertThat(readService.transactions(LEDGER_ID, queryBuilder.transactions(JAN_1, MAR_31, childCategory)))
            .extracting(AnalysisTransactionFact::id)
            .containsExactly(14L);
    }

    @Test
    void merchantAndKeywordWildcardsAreMatchedLiterally() {
        LocalDate may1 = LocalDate.of(2026, 5, 1);
        LocalDate may31 = LocalDate.of(2026, 5, 31);

        AnalysisFilters merchantWildcard = filters(null, null, List.of(), "星%克", null, null, null);
        AnalysisAggregateFact merchantFact = readService.aggregate(LEDGER_ID,
            queryBuilder.aggregate(TransactionType.EXPENSE, may1, may31, merchantWildcard));
        assertThat(merchantFact.count()).isEqualTo(1L);
        assertThat(merchantFact.amount()).isEqualByComparingTo("7.00");
        assertThat(readService.transactions(LEDGER_ID, queryBuilder.transactions(may1, may31, merchantWildcard)))
            .extracting(AnalysisTransactionFact::merchant)
            .containsExactly("星%克");

        AnalysisFilters keywordPercent = filters(null, null, List.of(), null, "%", null, null);
        List<AnalysisTransactionFact> keywordRows = readService.transactions(LEDGER_ID,
            queryBuilder.transactions(may1, may31, keywordPercent));
        assertThat(keywordRows).extracting(AnalysisTransactionFact::id).containsExactly(17L);
        assertThat(keywordRows).extracting(AnalysisTransactionFact::description)
            .containsExactly("含百分号%的支出");
    }

    private static AnalysisFilters filters(Long categoryId, Long accountId, List<Long> tagIds,
                                           String merchant, String keyword,
                                           BigDecimal minAmount, BigDecimal maxAmount) {
        return new AnalysisFilters(categoryId, accountId, tagIds, merchant, keyword, minAmount, maxAmount);
    }

    private static final class QueryBuilder {
        private NormalizedAnalysisQuery aggregate(TransactionType transactionType, LocalDate start, LocalDate end) {
            return aggregate(transactionType, start, end, AnalysisFilters.empty());
        }

        private NormalizedAnalysisQuery aggregate(TransactionType transactionType, LocalDate start, LocalDate end,
                                                  AnalysisFilters filters) {
            return new NormalizedAnalysisQuery(
                "agg",
                AnalysisQueryKind.AGGREGATE,
                AnalysisMetric.SUM,
                transactionType,
                AnalysisPeriodSpec.explicit(start, end),
                new AnalysisDateRange(start, end),
                filters,
                null,
                null,
                AnalysisSortField.AMOUNT,
                AnalysisSortDirection.DESC,
                50,
                "aggregate");
        }

        private NormalizedAnalysisQuery breakdown(AnalysisDimension dimension,
                                                  TransactionType transactionType,
                                                  LocalDate start,
                                                  LocalDate end) {
            return breakdown(dimension, transactionType, start, end, 50);
        }

        private NormalizedAnalysisQuery breakdown(AnalysisDimension dimension,
                                                  TransactionType transactionType,
                                                  LocalDate start,
                                                  LocalDate end,
                                                  int limit) {
            return new NormalizedAnalysisQuery(
                "brk",
                AnalysisQueryKind.BREAKDOWN,
                AnalysisMetric.SUM,
                transactionType,
                AnalysisPeriodSpec.explicit(start, end),
                new AnalysisDateRange(start, end),
                AnalysisFilters.empty(),
                dimension,
                null,
                AnalysisSortField.AMOUNT,
                AnalysisSortDirection.DESC,
                limit,
                "breakdown");
        }

        private NormalizedAnalysisQuery trend(AnalysisTimeGrain timeGrain,
                                              TransactionType transactionType,
                                              LocalDate start,
                                              LocalDate end) {
            return new NormalizedAnalysisQuery(
                "trd",
                AnalysisQueryKind.TREND,
                AnalysisMetric.SUM,
                transactionType,
                AnalysisPeriodSpec.explicit(start, end),
                new AnalysisDateRange(start, end),
                AnalysisFilters.empty(),
                null,
                timeGrain,
                AnalysisSortField.DATE,
                AnalysisSortDirection.ASC,
                50,
                "trend");
        }

        private NormalizedAnalysisQuery transactions(LocalDate start, LocalDate end) {
            return transactions(start, end, AnalysisFilters.empty());
        }

        private NormalizedAnalysisQuery transactions(LocalDate start, LocalDate end, AnalysisFilters filters) {
            return new NormalizedAnalysisQuery(
                "tx",
                AnalysisQueryKind.TRANSACTIONS,
                AnalysisMetric.SUM,
                null,
                AnalysisPeriodSpec.explicit(start, end),
                new AnalysisDateRange(start, end),
                filters,
                null,
                null,
                AnalysisSortField.DATE,
                AnalysisSortDirection.DESC,
                50,
                "transactions");
        }
    }
}
