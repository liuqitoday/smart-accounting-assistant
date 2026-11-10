package com.liuqitech.accountingassistant.service.analysis;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisAggregateFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisBreakdownFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTransactionFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.enums.AnalysisDimension;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.util.SqlLike;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AnalysisReadService {

    private static final String PARENT_CATEGORY_EXPR =
        "COALESCE(NULLIF(t.parent_category_name, ''), NULLIF(t.category, ''), '未分类')";
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final JdbcTemplate jdbcTemplate;
    private final AnalysisPeriodResolver periodResolver;

    public AnalysisReadService(JdbcTemplate jdbcTemplate, AnalysisPeriodResolver periodResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.periodResolver = periodResolver;
    }

    public AnalysisAggregateFact aggregate(Long ledgerId, NormalizedAnalysisQuery query) {
        Objects.requireNonNull(query, "query");
        StringBuilder sql = new StringBuilder("SELECT ")
            .append(metricAmountExpr(query)).append(" AS amount, COUNT(*) AS cnt ")
            .append("FROM transactions t WHERE ");
        List<Object> params = new ArrayList<>();
        appendLedgerAndBaseFilters(sql, params, ledgerId, query);
        appendDateRange(sql, params, query);
        appendQueryFilters(sql, params, ledgerId, query);

        List<AnalysisAggregateFact> rows = jdbcTemplate.query(
            sql.toString(), params.toArray(), (rs, rowNum) -> new AnalysisAggregateFact(
                scale(rs.getBigDecimal("amount")),
                rs.getLong("cnt")));
        if (rows.isEmpty()) {
            return new AnalysisAggregateFact(ZERO, 0L);
        }
        AnalysisAggregateFact fact = rows.get(0);
        return new AnalysisAggregateFact(scale(fact.amount()), fact.count());
    }

    /**
     * 当前期与紧邻的上一同期一次扫描完成；日期列保持裸比较以使用账本日期索引。
     */
    public SummaryAmounts summary(Long ledgerId, LocalDate currentStart, LocalDate currentEnd,
                                  LocalDate previousStart) {
        String sql = "SELECT CASE WHEN t.transaction_date >= ? THEN 1 ELSE 0 END AS current_period, "
            + "t.type AS tx_type, COALESCE(SUM(t.amount), 0) AS amount, COUNT(*) AS cnt "
            + "FROM transactions t WHERE t.ledger_id = ? AND t.deleted_at IS NULL "
            + "AND t.type IN ('INCOME', 'EXPENSE') AND t.transaction_date BETWEEN ? AND ? "
            + "GROUP BY current_period, t.type";
        List<SummaryRow> rows = jdbcTemplate.query(sql,
            new Object[] {currentStart.toString(), ledgerId, previousStart.toString(), currentEnd.toString()},
            (rs, rowNum) -> new SummaryRow(rs.getBoolean("current_period"),
                rs.getString("tx_type"), scale(rs.getBigDecimal("amount")), rs.getLong("cnt")));

        BigDecimal income = ZERO;
        BigDecimal expense = ZERO;
        BigDecimal previousIncome = ZERO;
        BigDecimal previousExpense = ZERO;
        long count = 0;
        for (SummaryRow row : rows) {
            if (row.current()) {
                count += row.count();
                if ("INCOME".equals(row.type())) income = row.amount();
                else expense = row.amount();
            } else if ("INCOME".equals(row.type())) {
                previousIncome = row.amount();
            } else {
                previousExpense = row.amount();
            }
        }
        return new SummaryAmounts(income, expense, count, previousIncome, previousExpense);
    }

    public AnalysisBreakdownFact breakdown(Long ledgerId, NormalizedAnalysisQuery query) {
        Objects.requireNonNull(query, "query");
        AnalysisDimension dimension = Objects.requireNonNull(query.dimension(), "dimension");
        DimensionSql dim = dimensionSql(dimension, ledgerId);

        StringBuilder sql = new StringBuilder("SELECT ")
            .append(dim.idExpr).append(" AS row_id, ")
            .append(dim.labelExpr).append(" AS label, ")
            .append(dim.parentLabelExpr).append(" AS parent_label, ")
            .append(metricAmountExpr(query)).append(" AS value, COUNT(*) AS cnt ")
            .append("FROM transactions t ").append(dim.join)
            .append("WHERE ");
        List<Object> params = new ArrayList<>(dim.joinParams);
        appendLedgerAndBaseFilters(sql, params, ledgerId, query);
        appendDateRange(sql, params, query);
        appendQueryFilters(sql, params, ledgerId, query);
        sql.append("GROUP BY ").append(dim.idExpr).append(", ")
            .append(dim.labelExpr).append(", ")
            .append(dim.parentLabelExpr).append(" ");
        sql.append(breakdownOrderBy(query));

        List<AnalysisBreakdownFact.Row> allRows = jdbcTemplate.query(
            sql.toString(), params.toArray(), this::mapBreakdownRow);

        BigDecimal denominator = allRows.stream()
            .map(AnalysisBreakdownFact.Row::value)
            .reduce(ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        int limit = query.limit() > 0 ? query.limit() : allRows.size();
        List<AnalysisBreakdownFact.Row> display = allRows.size() > limit
            ? List.copyOf(allRows.subList(0, limit))
            : List.copyOf(allRows);
        return new AnalysisBreakdownFact(display, denominator, dimension == AnalysisDimension.TAG);
    }

    public AnalysisTrendFact trend(Long ledgerId, NormalizedAnalysisQuery query) {
        Objects.requireNonNull(query, "query");
        AnalysisTimeGrain grain = Objects.requireNonNull(query.timeGrain(), "timeGrain");
        List<String> buckets = generateBuckets(query.period(), grain);
        if (buckets.isEmpty()) {
            return new AnalysisTrendFact(List.of());
        }

        StringBuilder filters = new StringBuilder("FROM transactions t WHERE ");
        List<Object> params = new ArrayList<>();
        appendLedgerAndBaseFilters(filters, params, ledgerId, query);
        appendDateRange(filters, params, query);
        appendQueryFilters(filters, params, ledgerId, query);
        String sql = "SELECT " + trendPeriodExpr(grain) + " AS period_label, t.type AS tx_type, "
            + "COALESCE(SUM(" + roundedTrendCentsExpr() + "), 0) AS amount_cents, COUNT(*) AS cnt "
            + filters + "GROUP BY period_label, t.type";

        Map<String, BucketAcc> byPeriod = new LinkedHashMap<>();
        for (String bucket : buckets) {
            byPeriod.put(bucket, new BucketAcc());
        }
        try {
            List<TrendBucketRow> rows = jdbcTemplate.query(
                sql, params.toArray(), (rs, rowNum) -> new TrendBucketRow(
                    rs.getString("period_label"), rs.getString("tx_type"),
                    BigDecimal.valueOf(rs.getLong("amount_cents"), 2), rs.getLong("cnt")));
            for (TrendBucketRow row : rows) {
                BucketAcc acc = byPeriod.get(row.period());
                if (acc == null) continue;
                acc.count += row.count();
                if ("INCOME".equals(row.type())) {
                    acc.income = row.amount();
                    acc.incomeCount = row.count();
                } else if ("EXPENSE".equals(row.type())) {
                    acc.expense = row.amount();
                    acc.expenseCount = row.count();
                }
            }
        } catch (DataAccessException error) {
            if (!isSqliteIntegerOverflow(error)) throw error;
            // SQLite reports overflow instead of silently converting an integer SUM to REAL.
            // Extremely large totals retain the previous exact BigDecimal behavior. Stream rows
            // into the bounded bucket map; ordinary totals still use one grouped SQL query.
            accumulateExactTrend(filters.toString(), params, grain, byPeriod);
        }

        AnalysisMetric metric = query.metric() == null ? AnalysisMetric.SUM : query.metric();
        List<AnalysisTrendFact.Point> points = new ArrayList<>(buckets.size());
        for (String bucket : buckets) {
            BucketAcc acc = byPeriod.get(bucket);
            BigDecimal income = scale(acc.income);
            BigDecimal expense = scale(acc.expense);
            BigDecimal value = trendValue(metric, query.transactionType(), acc);
            points.add(new AnalysisTrendFact.Point(bucket, income, expense, value, acc.count));
        }
        return new AnalysisTrendFact(List.copyOf(points));
    }

    public List<AnalysisTransactionFact> transactions(Long ledgerId, NormalizedAnalysisQuery query) {
        Objects.requireNonNull(query, "query");
        StringBuilder sql = new StringBuilder(
            "SELECT t.id, t.amount, t.type, t.transaction_date, t.description, t.category, "
                + "t.parent_category_name, a.name AS account_name, t.parsed_merchant "
                + "FROM transactions t "
                + "LEFT JOIN accounts a ON a.id = t.account_id AND a.ledger_id = ? "
                + "WHERE ");
        List<Object> params = new ArrayList<>();
        params.add(ledgerId);
        appendLedgerAndBaseFilters(sql, params, ledgerId, query);
        appendDateRange(sql, params, query);
        appendQueryFilters(sql, params, ledgerId, query);
        sql.append(transactionOrderBy(query));
        if (query.limit() > 0) {
            sql.append(" LIMIT ").append(query.limit());
        }

        return jdbcTemplate.query(sql.toString(), params.toArray(), this::mapTransaction);
    }

    private void appendLedgerAndBaseFilters(StringBuilder sql, List<Object> params,
                                            Long ledgerId, NormalizedAnalysisQuery query) {
        sql.append("t.ledger_id = ? AND t.deleted_at IS NULL AND t.type <> 'TRANSFER' ");
        params.add(ledgerId);
        if (query.transactionType() != null) {
            sql.append("AND t.type = ? ");
            params.add(query.transactionType().name());
        }
    }

    private void appendDateRange(StringBuilder sql, List<Object> params, NormalizedAnalysisQuery query) {
        sql.append("AND t.transaction_date BETWEEN ? AND ? ");
        params.add(query.period().start().toString());
        params.add(query.period().end().toString());
    }

    private void appendQueryFilters(StringBuilder sql, List<Object> params,
                                    Long ledgerId, NormalizedAnalysisQuery query) {
        AnalysisFilters filters = query.filters() == null ? AnalysisFilters.empty() : query.filters();
        if (filters.categoryId() != null) {
            sql.append("AND (t.category_id = ? OR t.parent_category_id = ?) ");
            params.add(filters.categoryId());
            params.add(filters.categoryId());
        }
        if (filters.accountId() != null) {
            sql.append("AND t.account_id = ? ");
            params.add(filters.accountId());
        }
        if (filters.tagIds() != null && !filters.tagIds().isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(filters.tagIds().size(), "?"));
            sql.append("AND EXISTS (SELECT 1 FROM transaction_tags tt JOIN tags tg ON tg.id = tt.tag_id ")
                .append("WHERE tt.transaction_id = t.id AND tt.tag_id IN (")
                .append(placeholders)
                .append(") AND (tg.is_system = 1 OR tg.ledger_id = ?)) ");
            params.addAll(filters.tagIds());
            params.add(ledgerId);
        }
        if (filters.merchant() != null && !filters.merchant().isBlank()) {
            sql.append("AND t.parsed_merchant LIKE ? ESCAPE '").append(SqlLike.ESCAPE).append("' ");
            params.add(SqlLike.contains(filters.merchant()));
        }
        if (filters.keyword() != null && !filters.keyword().isBlank()) {
            sql.append("AND (t.description LIKE ? ESCAPE '").append(SqlLike.ESCAPE)
                .append("' OR t.original_text LIKE ? ESCAPE '").append(SqlLike.ESCAPE)
                .append("' OR t.note LIKE ? ESCAPE '").append(SqlLike.ESCAPE).append("') ");
            String kw = SqlLike.contains(filters.keyword());
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (filters.minAmount() != null) {
            sql.append("AND t.amount >= ? ");
            params.add(filters.minAmount());
        }
        if (filters.maxAmount() != null) {
            sql.append("AND t.amount <= ? ");
            params.add(filters.maxAmount());
        }
    }

    private String metricAmountExpr(NormalizedAnalysisQuery query) {
        AnalysisMetric metric = query.metric() == null ? AnalysisMetric.SUM : query.metric();
        return switch (metric) {
            case COUNT -> "COUNT(*)";
            case AVG -> "COALESCE(AVG(t.amount), 0)";
            case SUM -> "COALESCE(SUM(t.amount), 0)";
        };
    }

    private DimensionSql dimensionSql(AnalysisDimension dimension, Long ledgerId) {
        return switch (dimension) {
            case PARENT_CATEGORY -> new DimensionSql(
                "t.parent_category_id",
                PARENT_CATEGORY_EXPR,
                "NULL",
                "",
                List.of());
            case CHILD_CATEGORY -> new DimensionSql(
                "t.category_id",
                "COALESCE(NULLIF(t.category, ''), '未分类')",
                "NULLIF(t.parent_category_name, '')",
                "",
                List.of());
            case ACCOUNT -> new DimensionSql(
                "a.id",
                "COALESCE(a.name, '未指定账户')",
                "NULL",
                "LEFT JOIN accounts a ON a.id = t.account_id AND a.ledger_id = ? ",
                List.of(ledgerId));
            case MERCHANT -> new DimensionSql(
                "NULL",
                "COALESCE(NULLIF(TRIM(t.parsed_merchant), ''), '未知商家')",
                "NULL",
                "",
                List.of());
            case TAG -> new DimensionSql(
                "tg.id",
                "tg.name",
                "NULL",
                "INNER JOIN transaction_tags tt ON tt.transaction_id = t.id "
                    + "INNER JOIN tags tg ON tg.id = tt.tag_id AND (tg.is_system = 1 OR tg.ledger_id = ?) ",
                List.of(ledgerId));
        };
    }

    private String breakdownOrderBy(NormalizedAnalysisQuery query) {
        AnalysisSortField field = query.sortField() == null ? AnalysisSortField.AMOUNT : query.sortField();
        AnalysisSortDirection direction =
            query.sortDirection() == null ? AnalysisSortDirection.DESC : query.sortDirection();
        String dir = direction.name();
        String primary = field == AnalysisSortField.DATE ? "label" : "value";
        return "ORDER BY " + primary + " " + dir + ", label ASC ";
    }

    private String transactionOrderBy(NormalizedAnalysisQuery query) {
        AnalysisSortField field = query.sortField() == null ? AnalysisSortField.DATE : query.sortField();
        AnalysisSortDirection direction =
            query.sortDirection() == null ? AnalysisSortDirection.DESC : query.sortDirection();
        String col = field == AnalysisSortField.AMOUNT ? "t.amount" : "t.transaction_date";
        return "ORDER BY " + col + " " + direction.name() + ", t.id DESC";
    }

    private AnalysisBreakdownFact.Row mapBreakdownRow(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getObject("row_id") == null ? null : rs.getLong("row_id");
        return new AnalysisBreakdownFact.Row(
            id,
            rs.getString("label"),
            rs.getString("parent_label"),
            scale(rs.getBigDecimal("value")),
            rs.getLong("cnt"));
    }

    private AnalysisTransactionFact mapTransaction(ResultSet rs, int rowNum) throws SQLException {
        return new AnalysisTransactionFact(
            rs.getLong("id"),
            scale(rs.getBigDecimal("amount")),
            TransactionType.valueOf(rs.getString("type")),
            LocalDate.parse(rs.getString("transaction_date")),
            rs.getString("description"),
            rs.getString("category"),
            rs.getString("parent_category_name"),
            rs.getString("account_name"),
            rs.getString("parsed_merchant"));
    }

    private List<String> generateBuckets(AnalysisDateRange range, AnalysisTimeGrain grain) {
        int count = periodResolver.bucketCount(range, grain);
        List<String> buckets = new ArrayList<>(count);
        LocalDate cursor = switch (grain) {
            case DAY -> range.start();
            case WEEK -> range.start().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> range.start().withDayOfMonth(1);
            case QUARTER -> quarterStart(range.start());
            case YEAR -> range.start().withDayOfYear(1);
        };
        for (int i = 0; i < count; i++) {
            buckets.add(periodLabel(cursor, grain));
            cursor = switch (grain) {
                case DAY -> cursor.plusDays(1);
                case WEEK -> cursor.plusWeeks(1);
                case MONTH -> cursor.plusMonths(1);
                case QUARTER -> cursor.plusMonths(3);
                case YEAR -> cursor.plusYears(1);
            };
        }
        return buckets;
    }

    private String periodLabel(LocalDate date, AnalysisTimeGrain grain) {
        return switch (grain) {
            case DAY -> date.toString();
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            case MONTH -> String.format("%04d-%02d", date.getYear(), date.getMonthValue());
            case QUARTER -> date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case YEAR -> String.format("%04d", date.getYear());
        };
    }

    private String trendPeriodExpr(AnalysisTimeGrain grain) {
        return switch (grain) {
            case DAY -> "t.transaction_date";
            // SQLite %w 从周日 0 开始；减去偏移获得周一，跨月、跨年仍与 Java 桶标签一致。
            case WEEK -> "date(t.transaction_date, '-' || "
                + "((CAST(strftime('%w', t.transaction_date) AS INTEGER) + 6) % 7) || ' days')";
            case MONTH -> "substr(t.transaction_date, 1, 7)";
            case QUARTER -> "substr(t.transaction_date, 1, 4) || '-Q' || "
                + "((CAST(substr(t.transaction_date, 6, 2) AS INTEGER) + 2) / 3)";
            case YEAR -> "substr(t.transaction_date, 1, 4)";
        };
    }

    private String roundedTrendCentsExpr() {
        // SQLite ROUND(1.005, 2) 会受二进制浮点影响得到 1.00；原 JDBC BigDecimal
        // 映射按十进制 HALF_UP 得到 1.01。按同一文本表示检查第三位小数，保留旧行的
        // 逐笔舍入语义。直接拼整数分，避免 1.15 * 100 = 114.999... 的误差；
        // 聚合也用整数，不能把每笔舍入后交给浮点 SUM，否则大额合计会丢分。
        String text = "CAST(t.amount AS TEXT)";
        String dot = "instr(" + text + ", '.')";
        return "CASE WHEN " + dot + " > 0 AND instr(lower(" + text + "), 'e') = 0 "
            + "THEN CAST(replace(substr(" + text + " || '00', 1, " + dot + " + 2), '.', '') AS INTEGER) "
            + "+ CASE WHEN substr(" + text + ", " + dot + " + 3, 1) >= '5' "
            + "THEN CASE WHEN t.amount < 0 THEN -1 ELSE 1 END ELSE 0 END "
            + "ELSE CAST(ROUND(t.amount * 100, 0) AS INTEGER) END";
    }

    private boolean isSqliteIntegerOverflow(DataAccessException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLiteException sqlite
                    && sqlite.getResultCode() == SQLiteErrorCode.SQLITE_ERROR
                    && sqlite.getMessage().contains("integer overflow")) return true;
        }
        return false;
    }

    private void accumulateExactTrend(String filters, List<Object> params, AnalysisTimeGrain grain,
                                      Map<String, BucketAcc> byPeriod) {
        jdbcTemplate.query("SELECT t.transaction_date, t.type, t.amount " + filters, params.toArray(),
            (RowCallbackHandler) rs -> {
                BucketAcc acc = byPeriod.get(periodLabel(LocalDate.parse(rs.getString("transaction_date")), grain));
                if (acc == null) return;
                BigDecimal amount = scale(rs.getBigDecimal("amount"));
                acc.count++;
                if ("INCOME".equals(rs.getString("type"))) {
                    acc.income = acc.income.add(amount);
                    acc.incomeCount++;
                } else if ("EXPENSE".equals(rs.getString("type"))) {
                    acc.expense = acc.expense.add(amount);
                    acc.expenseCount++;
                }
            });
    }

    private BigDecimal trendValue(AnalysisMetric metric, TransactionType type, BucketAcc acc) {
        return switch (metric) {
            case COUNT -> BigDecimal.valueOf(selectedCount(type, acc)).setScale(2, RoundingMode.HALF_UP);
            case AVG -> {
                long n = selectedCount(type, acc);
                if (n == 0) {
                    yield ZERO;
                }
                yield selectedSum(type, acc).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            }
            case SUM -> scale(selectedSum(type, acc));
        };
    }

    private BigDecimal selectedSum(TransactionType type, BucketAcc acc) {
        if (type == TransactionType.EXPENSE) {
            return acc.expense;
        }
        if (type == TransactionType.INCOME) {
            return acc.income;
        }
        return acc.income.add(acc.expense);
    }

    private long selectedCount(TransactionType type, BucketAcc acc) {
        if (type == TransactionType.EXPENSE) {
            return acc.expenseCount;
        }
        if (type == TransactionType.INCOME) {
            return acc.incomeCount;
        }
        return acc.count;
    }

    private static LocalDate quarterStart(LocalDate date) {
        int month = ((date.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(date.getYear(), month, 1);
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record DimensionSql(String idExpr, String labelExpr, String parentLabelExpr,
                                String join, List<Object> joinParams) {}

    public record SummaryAmounts(BigDecimal income, BigDecimal expense, long count,
                                 BigDecimal previousIncome, BigDecimal previousExpense) {}

    private record SummaryRow(boolean current, String type, BigDecimal amount, long count) {}

    private record TrendBucketRow(String period, String type, BigDecimal amount, long count) {}

    private static final class BucketAcc {
        private BigDecimal income = ZERO;
        private BigDecimal expense = ZERO;
        private long count;
        private long incomeCount;
        private long expenseCount;
    }
}
