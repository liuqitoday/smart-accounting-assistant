package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.AnalysisQuery;
import com.liuqitech.accountingassistant.enums.AnalysisGroupBy;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisQueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisQueryExecutorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AnalysisQueryExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AnalysisQueryExecutor(jdbcTemplate);
    }

    @Test
    void aggregateSumExpenseBuildsCorrectSql() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.AGGREGATE);
        query.setMetric(AnalysisMetric.SUM);
        query.setTxType("EXPENSE");
        query.setDateStart("2025-01-01");
        query.setDateEnd("2025-12-31");

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.<Object[]>of(new Object[]{new BigDecimal("12345.67")}));

        List<Object[]> result = executor.execute(1L, query);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("12345.67"), result.get(0)[0]);

        verify(jdbcTemplate).query(
            contains("SUM(t.amount)"),
            argThat((Object[] args) ->
                args[0].equals(1L) &&
                args[1].equals("EXPENSE") &&
                args[2].equals("2025-01-01") &&
                args[3].equals("2025-12-31")
            ),
            any(RowMapper.class)
        );
    }

    @Test
    void aggregateCountAllBuildsCorrectSql() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.AGGREGATE);
        query.setMetric(AnalysisMetric.COUNT);
        query.setTxType("ALL");
        query.setDateStart("2026-01-01");
        query.setDateEnd("2026-12-31");

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.<Object[]>of(new Object[]{42L}));

        List<Object[]> result = executor.execute(2L, query);

        assertEquals(42L, result.get(0)[0]);
        verify(jdbcTemplate).query(contains("COUNT(*)"), any(Object[].class), any(RowMapper.class));
    }

    @Test
    void topNLimitExceedsMaxGetsClampedTo50() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.TOP_N);
        query.setTxType("EXPENSE");
        query.setDateStart("2025-01-01");
        query.setDateEnd("2025-12-31");
        query.setLimit(999);  // 超过 MAX_LIMIT

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.of());

        executor.execute(1L, query);

        verify(jdbcTemplate).query(contains("LIMIT 50"), any(Object[].class), any(RowMapper.class));
    }

    @Test
    void topNLimitZeroOrNegativeGetsDefaultedTo10() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.TOP_N);
        query.setTxType("EXPENSE");
        query.setDateStart("2025-01-01");
        query.setDateEnd("2025-12-31");
        query.setLimit(-5);  // SQLite 负数 LIMIT = 无限制，须兜底

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.of());

        executor.execute(1L, query);

        verify(jdbcTemplate).query(contains("LIMIT 10"), any(Object[].class), any(RowMapper.class));
    }

    @Test
    void topNOrdersByAmountDescWithIdTiebreaker() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.TOP_N);
        query.setTxType("EXPENSE");
        query.setDateStart("2025-01-01");
        query.setDateEnd("2025-12-31");
        query.setLimit(10);

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.of());

        executor.execute(1L, query);

        // 金额并列时按 t.id DESC 兜底，保证 TOP_N 确定性
        verify(jdbcTemplate).query(contains("ORDER BY t.amount DESC, t.id DESC"),
            any(Object[].class), any(RowMapper.class));
    }

    @Test
    void groupByOrdersByValueDescWithLabelTiebreaker() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.GROUP_BY);
        query.setGroupBy(AnalysisGroupBy.CATEGORY);
        query.setMetric(AnalysisMetric.SUM);
        query.setTxType("EXPENSE");
        query.setDateStart("2025-01-01");
        query.setDateEnd("2025-12-31");
        query.setLimit(10);

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.of());

        executor.execute(1L, query);

        // value 并列时按 label 字典序兜底，保证 GROUP_BY 排名确定性
        verify(jdbcTemplate).query(contains("ORDER BY value DESC, label ASC"),
            any(Object[].class), any(RowMapper.class));
    }

    @Test
    void trendExpenseAppendsTypeFilter() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.TREND);
        query.setGroupBy(AnalysisGroupBy.MONTH);
        query.setTxType("EXPENSE");
        query.setDateStart("2026-01-01");
        query.setDateEnd("2026-03-31");

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.of());

        executor.execute(1L, query);

        verify(jdbcTemplate).query(
            contains("t.type = ?"),
            argThat((Object[] args) ->
                args[0].equals(1L) &&
                args[1].equals("EXPENSE") &&
                args[2].equals("2026-01-01") &&
                args[3].equals("2026-03-31")
            ),
            any(RowMapper.class)
        );
    }

    @Test
    void groupByCategoryFallsBackFromParentToCategoryToUncategorized() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.GROUP_BY);
        query.setGroupBy(AnalysisGroupBy.CATEGORY);
        query.setMetric(AnalysisMetric.SUM);
        query.setTxType("EXPENSE");
        query.setDateStart("2025-01-01");
        query.setDateEnd("2025-12-31");
        query.setLimit(10);

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.of());

        executor.execute(1L, query);

        verify(jdbcTemplate).query(
            contains("COALESCE(NULLIF(t.parent_category_name, ''), NULLIF(t.category, ''), '未分类')"),
            any(Object[].class),
            any(RowMapper.class)
        );
    }

    @Test
    void groupByAccountConstrainsLedgerOnJoin() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.GROUP_BY);
        query.setGroupBy(AnalysisGroupBy.ACCOUNT);
        query.setMetric(AnalysisMetric.SUM);
        query.setTxType("EXPENSE");
        query.setDateStart("2025-01-01");
        query.setDateEnd("2025-12-31");
        query.setLimit(10);

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.of());

        executor.execute(1L, query);

        verify(jdbcTemplate).query(
            contains("a.ledger_id = ?"),
            argThat((Object[] args) -> args[0].equals(1L) && args[1].equals(1L)),
            any(RowMapper.class)
        );
    }

    @Test
    void groupByTagConstrainsSystemOrCurrentLedger() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.GROUP_BY);
        query.setGroupBy(AnalysisGroupBy.TAG);
        query.setMetric(AnalysisMetric.SUM);
        query.setTxType("EXPENSE");
        query.setDateStart("2025-01-01");
        query.setDateEnd("2025-12-31");
        query.setLimit(10);

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.of());

        executor.execute(1L, query);

        verify(jdbcTemplate).query(
            contains("tg.is_system = 1 OR tg.ledger_id = ?"),
            argThat((Object[] args) -> args[0].equals(1L) && args[1].equals(1L)),
            any(RowMapper.class)
        );
    }

    @Test
    void dateStartAfterDateEndGetsSwapped() {
        AnalysisQuery query = new AnalysisQuery();
        query.setQueryType(AnalysisQueryType.AGGREGATE);
        query.setMetric(AnalysisMetric.COUNT);
        query.setTxType("ALL");
        query.setDateStart("2025-12-31");
        query.setDateEnd("2025-01-01");

        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(List.<Object[]>of(new Object[]{10L}));

        executor.execute(1L, query);

        verify(jdbcTemplate).query(anyString(),
            argThat((Object[] args) ->
                args[1].equals("2025-01-01") && args[2].equals("2025-12-31")
            ),
            any(RowMapper.class));
    }
}
