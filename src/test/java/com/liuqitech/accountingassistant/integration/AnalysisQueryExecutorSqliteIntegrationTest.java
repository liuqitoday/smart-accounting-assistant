package com.liuqitech.accountingassistant.integration;

import com.liuqitech.accountingassistant.dto.AnalysisQuery;
import com.liuqitech.accountingassistant.enums.AnalysisGroupBy;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisQueryType;
import com.liuqitech.accountingassistant.service.AnalysisQueryExecutor;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisQueryExecutorSqliteIntegrationTest {

    private static final String JDBC_URL = "jdbc:sqlite:file:analysis-test?mode=memory&cache=shared";

    private static Connection keepAlive;
    private static AnalysisQueryExecutor executor;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(JDBC_URL);
        keepAlive = dataSource.getConnection();
        ScriptUtils.executeSqlScript(keepAlive, new ClassPathResource("analysis/sqlite-fixture.sql"));
        executor = new AnalysisQueryExecutor(new JdbcTemplate(dataSource));
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    @Test
    void sqliteTrendHonorsExpenseTypeAndLedgerBoundary() {
        // 该段沿用 V1 迁移期 DTO，仅验证旧执行器；V2 测试使用 NormalizedAnalysisQuery。
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.TREND);
        query.setGroupBy(AnalysisGroupBy.MONTH);
        query.setTxType("EXPENSE");
        query.setDateStart("2026-01-01");
        query.setDateEnd("2026-03-31");

        List<Object[]> rows = executor.execute(100L, query);

        assertThat(rows).extracting(row -> row[0]).containsExactly("2026-01", "2026-03");
        assertThat(rows).allSatisfy(row -> assertThat((BigDecimal) row[2]).isPositive());
    }

    @Test
    void sqliteGroupByCategoryFallsBackFromParentToCategoryToUncategorized() {
        AnalysisQuery query = groupByQuery(AnalysisGroupBy.CATEGORY);

        List<Object[]> rows = executor.execute(100L, query);

        assertThat(rows).extracting(row -> row[0])
            .contains("餐饮", "零食", "未分类")
            .doesNotContain("");
    }

    @Test
    void sqliteGroupByAccountHonorsLedgerOnJoin() {
        AnalysisQuery query = groupByQuery(AnalysisGroupBy.ACCOUNT);

        List<Object[]> rows = executor.execute(100L, query);

        assertThat(rows).extracting(row -> row[0])
            .contains("支付宝", "未指定账户")
            .doesNotContain("其他账本账户");
    }

    @Test
    void sqliteGroupByTagHonorsSystemOrCurrentLedger() {
        AnalysisQuery query = groupByQuery(AnalysisGroupBy.TAG);

        List<Object[]> rows = executor.execute(100L, query);

        assertThat(rows).extracting(row -> row[0])
            .contains("旅行", "工作")
            .doesNotContain("其他账本标签");
    }

    private static AnalysisQuery groupByQuery(AnalysisGroupBy groupBy) {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.GROUP_BY);
        query.setGroupBy(groupBy);
        query.setMetric(AnalysisMetric.SUM);
        query.setTxType("EXPENSE");
        query.setDateStart("2026-01-01");
        query.setDateEnd("2026-03-31");
        query.setLimit(10);
        return query;
    }
}
