package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.AnalysisQuery;
import com.liuqitech.accountingassistant.enums.AnalysisGroupBy;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisOrderBy;
import com.liuqitech.accountingassistant.enums.AnalysisQueryType;
import com.liuqitech.accountingassistant.util.AppClock;
import com.liuqitech.accountingassistant.util.SqlLike;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询计划执行器。ledger_id 隔离/软删除过滤/排除 TRANSFER 硬编码在 SQL 里，AI 不可触碰。
 * transaction_date 列恒为 YYYY-MM-DD 文本，WHERE 侧对列裸比较以命中 idx_tx_ledger_date；
 * 参数侧保留 date(?)，可顺带把 AI 给出的非法日期归一为 NULL（等价于查不到，行为不变）。
 */
@Service
public class AnalysisQueryExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisQueryExecutor.class);
    private static final int MAX_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;

    public AnalysisQueryExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Object[]> execute(Long ledgerId, AnalysisQuery query) {
        if (query.getQueryType() == null) {
            throw new IllegalArgumentException("queryType 不能为空");
        }

        // 防御性校验
        normalizeQuery(query);

        return switch (query.getQueryType()) {
            case AGGREGATE -> executeAggregate(ledgerId, query);
            case TOP_N -> executeTopN(ledgerId, query);
            case GROUP_BY -> executeGroupBy(ledgerId, query);
            case TREND -> executeTrend(ledgerId, query);
            case LIST -> executeList(ledgerId, query);
        };
    }

    private void normalizeQuery(AnalysisQuery query) {
        // limit 硬顶 50
        if (query.getLimit() != null && query.getLimit() > MAX_LIMIT) {
            query.setLimit(MAX_LIMIT);
        }
        // limit ≤ 0 兜底为 10（SQLite 负数 LIMIT = 无限制，须拦在代码层）
        if (query.getLimit() != null && query.getLimit() < 1) {
            query.setLimit(10);
        }

        // 缺省值兜底（AI 输出可能缺字段）
        if (query.getTxType() == null || query.getTxType().isBlank()) {
            query.setTxType("ALL");
        }
        if (query.getMetric() == null) {
            query.setMetric(AnalysisMetric.SUM);
        }

        // GROUP_BY 按时间维度分组 = 趋势查询；TREND 缺 groupBy 默认按月
        if (query.getQueryType() == AnalysisQueryType.GROUP_BY
                && (query.getGroupBy() == AnalysisGroupBy.MONTH || query.getGroupBy() == AnalysisGroupBy.DAY)) {
            query.setQueryType(AnalysisQueryType.TREND);
        }
        if (query.getQueryType() == AnalysisQueryType.TREND
                && query.getGroupBy() != AnalysisGroupBy.MONTH && query.getGroupBy() != AnalysisGroupBy.DAY) {
            query.setGroupBy(AnalysisGroupBy.MONTH);
        }
        if (query.getQueryType() == AnalysisQueryType.GROUP_BY && query.getGroupBy() == null) {
            query.setGroupBy(AnalysisGroupBy.CATEGORY);
        }

        // 日期缺失默认今年（按业务时区取"今天"，公网服务器系统时区可能是 UTC）
        if (query.getDateStart() == null) {
            query.setDateStart(AppClock.today().withDayOfYear(1).toString());
        }
        if (query.getDateEnd() == null) {
            query.setDateEnd(AppClock.today().toString());
        }

        // dateStart > dateEnd 自动互换
        if (query.getDateStart().compareTo(query.getDateEnd()) > 0) {
            String temp = query.getDateStart();
            query.setDateStart(query.getDateEnd());
            query.setDateEnd(temp);
        }
    }

    private List<Object[]> executeAggregate(Long ledgerId, AnalysisQuery query) {
        StringBuilder sql = new StringBuilder("SELECT ");

        // 指标
        switch (query.getMetric()) {
            case SUM -> sql.append("COALESCE(SUM(t.amount), 0)");
            case COUNT -> sql.append("COUNT(*)");
            case AVG -> sql.append("COALESCE(AVG(t.amount), 0)");
        }

        sql.append(" FROM transactions t WHERE t.ledger_id = ? AND t.deleted_at IS NULL ");
        sql.append("AND t.type <> 'TRANSFER' ");

        List<Object> params = new ArrayList<>();
        params.add(ledgerId);

        // 交易类型
        if (!"ALL".equals(query.getTxType())) {
            sql.append("AND t.type = ? ");
            params.add(query.getTxType());
        }

        // 日期范围
        sql.append("AND t.transaction_date BETWEEN date(?) AND date(?) ");
        params.add(query.getDateStart());
        params.add(query.getDateEnd());

        // 过滤条件
        appendFilters(sql, params, query, ledgerId);

        return jdbcTemplate.query(sql.toString(), params.toArray(),
            (rs, rowNum) -> new Object[]{rs.getObject(1)});
    }

    private List<Object[]> executeTopN(Long ledgerId, AnalysisQuery query) {
        int limit = query.getLimit() != null ? query.getLimit() : 10;

        StringBuilder sql = new StringBuilder(
            "SELECT t.id, t.amount, t.description, t.transaction_date, " +
            "t.category, t.parent_category_name, t.parsed_merchant " +
            "FROM transactions t WHERE t.ledger_id = ? AND t.deleted_at IS NULL " +
            "AND t.type <> 'TRANSFER' ");

        List<Object> params = new ArrayList<>();
        params.add(ledgerId);

        if (!"ALL".equals(query.getTxType())) {
            sql.append("AND t.type = ? ");
            params.add(query.getTxType());
        }

        sql.append("AND t.transaction_date BETWEEN date(?) AND date(?) ");
        params.add(query.getDateStart());
        params.add(query.getDateEnd());

        appendFilters(sql, params, query, ledgerId);

        // limit 已在 normalizeQuery 钳到 ≤50 的 int，可安全内联（保持 SQL 断言可读）
        // t.id DESC 次级排序：金额并列时行序未定义会 nondeterministic，按自增 id 兜底（与 executeList 一致）
        sql.append("ORDER BY t.amount DESC, t.id DESC LIMIT ").append(limit);

        return jdbcTemplate.query(sql.toString(), params.toArray(),
            (rs, rowNum) -> new Object[]{
                rs.getLong("id"),
                rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getString("transaction_date"),
                rs.getString("category"),
                rs.getString("parent_category_name"),
                rs.getString("parsed_merchant")
            });
    }

    private List<Object[]> executeTrend(Long ledgerId, AnalysisQuery query) {
        String timeFormat = query.getGroupBy() == AnalysisGroupBy.DAY ? "%Y-%m-%d" : "%Y-%m";

        StringBuilder sql = new StringBuilder("SELECT strftime('").append(timeFormat).append("', t.transaction_date) as period, ");

        switch (query.getMetric() != null ? query.getMetric() : AnalysisMetric.SUM) {
            case SUM -> sql.append("SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END) as income, ")
                           .append("SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) as expense ");
            case COUNT -> sql.append("SUM(CASE WHEN t.type = 'INCOME' THEN 1 ELSE 0 END) as income, ")
                             .append("SUM(CASE WHEN t.type = 'EXPENSE' THEN 1 ELSE 0 END) as expense ");
            case AVG -> sql.append("AVG(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE NULL END) as income, ")
                           .append("AVG(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE NULL END) as expense ");
        }

        sql.append("FROM transactions t WHERE t.ledger_id = ? AND t.deleted_at IS NULL ");
        sql.append("AND t.type <> 'TRANSFER' ");

        List<Object> params = new ArrayList<>();
        params.add(ledgerId);

        if (!"ALL".equals(query.getTxType())) {
            sql.append("AND t.type = ? ");
            params.add(query.getTxType());
        }

        sql.append("AND t.transaction_date BETWEEN date(?) AND date(?) ");
        params.add(query.getDateStart());
        params.add(query.getDateEnd());

        appendFilters(sql, params, query, ledgerId);

        sql.append("GROUP BY strftime('").append(timeFormat).append("', t.transaction_date) ORDER BY period ASC");

        return jdbcTemplate.query(sql.toString(), params.toArray(),
            (rs, rowNum) -> new Object[]{
                rs.getString("period"),
                rs.getBigDecimal("income"),
                rs.getBigDecimal("expense")
            });
    }

    private List<Object[]> executeGroupBy(Long ledgerId, AnalysisQuery query) {
        // label 表达式：直接在 SQL 层产出可读名称，前端不再做 id→名称映射
        String labelExpr;
        String join = "";
        List<Object> joinParams = new ArrayList<>();
        switch (query.getGroupBy()) {
            case CATEGORY -> labelExpr = "COALESCE(NULLIF(t.parent_category_name, ''), NULLIF(t.category, ''), '未分类')";
            case ACCOUNT -> {
                labelExpr = "COALESCE(a.name, '未指定账户')";
                join = "LEFT JOIN accounts a ON a.id = t.account_id AND a.ledger_id = ? ";
                joinParams.add(ledgerId);
            }
            case MERCHANT -> labelExpr = "COALESCE(NULLIF(TRIM(t.parsed_merchant), ''), '未知商家')";
            case TAG -> {
                labelExpr = "tg.name";
                join = "INNER JOIN transaction_tags tt ON tt.transaction_id = t.id " +
                       "INNER JOIN tags tg ON tg.id = tt.tag_id AND (tg.is_system = 1 OR tg.ledger_id = ?) ";
                joinParams.add(ledgerId);
            }
            default -> throw new IllegalArgumentException("GROUP_BY 不支持: " + query.getGroupBy());
        }

        String valueExpr = switch (query.getMetric()) {
            case COUNT -> "COUNT(*)";
            case AVG -> "AVG(t.amount)";
            case SUM -> "SUM(t.amount)";
        };

        StringBuilder sql = new StringBuilder("SELECT ").append(labelExpr).append(" AS label, ")
            .append(valueExpr).append(" AS value, COUNT(*) AS cnt ")
            .append("FROM transactions t ").append(join)
            .append("WHERE t.ledger_id = ? AND t.deleted_at IS NULL AND t.type <> 'TRANSFER' ");

        List<Object> params = new ArrayList<>();
        params.addAll(joinParams);
        params.add(ledgerId);

        if (!"ALL".equals(query.getTxType())) {
            sql.append("AND t.type = ? ");
            params.add(query.getTxType());
        }

        sql.append("AND t.transaction_date BETWEEN date(?) AND date(?) ");
        params.add(query.getDateStart());
        params.add(query.getDateEnd());

        appendFilters(sql, params, query, ledgerId);

        int limit = query.getLimit() != null ? query.getLimit() : 10;
        // value 并列时行序未定义会 nondeterministic；聚合行无 t.id 可兜底，按 label 别名字典序确定次级排序
        sql.append("GROUP BY label ORDER BY value DESC, label ASC LIMIT ").append(limit);

        return jdbcTemplate.query(sql.toString(), params.toArray(),
            (rs, rowNum) -> new Object[]{
                rs.getString("label"),
                rs.getObject("value"),
                rs.getObject("cnt")
            });
    }

    private List<Object[]> executeList(Long ledgerId, AnalysisQuery query) {
        int limit = query.getLimit() != null ? query.getLimit() : 20;

        // 列结构与 executeTopN 完全一致（附录 A 契约），仅排序不同
        StringBuilder sql = new StringBuilder(
            "SELECT t.id, t.amount, t.description, t.transaction_date, " +
            "t.category, t.parent_category_name, t.parsed_merchant " +
            "FROM transactions t WHERE t.ledger_id = ? AND t.deleted_at IS NULL " +
            "AND t.type <> 'TRANSFER' ");

        List<Object> params = new ArrayList<>();
        params.add(ledgerId);

        if (!"ALL".equals(query.getTxType())) {
            sql.append("AND t.type = ? ");
            params.add(query.getTxType());
        }

        sql.append("AND t.transaction_date BETWEEN date(?) AND date(?) ");
        params.add(query.getDateStart());
        params.add(query.getDateEnd());

        appendFilters(sql, params, query, ledgerId);

        String orderCol = query.getOrderBy() == AnalysisOrderBy.AMOUNT ? "t.amount" : "t.transaction_date";
        sql.append("ORDER BY ").append(orderCol).append(" DESC, t.id DESC LIMIT ").append(limit);

        return jdbcTemplate.query(sql.toString(), params.toArray(),
            (rs, rowNum) -> new Object[]{
                rs.getLong("id"),
                rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getString("transaction_date"),
                rs.getString("category"),
                rs.getString("parent_category_name"),
                rs.getString("parsed_merchant")
            });
    }

    private void appendFilters(StringBuilder sql, List<Object> params, AnalysisQuery query, Long ledgerId) {
        if (query.getCategoryId() != null) {
            sql.append("AND (t.category_id = ? OR t.parent_category_id = ?) ");
            params.add(query.getCategoryId());
            params.add(query.getCategoryId());
        }
        if (query.getAccountId() != null) {
            sql.append("AND t.account_id = ? ");
            params.add(query.getAccountId());
        }
        if (query.getTagId() != null) {
            sql.append("AND EXISTS (SELECT 1 FROM transaction_tags tt JOIN tags tg ON tg.id = tt.tag_id ");
            sql.append("WHERE tt.transaction_id = t.id AND tt.tag_id = ? AND (tg.is_system = 1 OR tg.ledger_id = ?)) ");
            params.add(query.getTagId());
            params.add(ledgerId);
        }
        if (query.getMerchant() != null && !query.getMerchant().isBlank()) {
            sql.append("AND t.parsed_merchant LIKE ? ESCAPE '").append(SqlLike.ESCAPE).append("' ");
            params.add(SqlLike.contains(query.getMerchant()));
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            sql.append("AND (t.description LIKE ? ESCAPE '").append(SqlLike.ESCAPE)
                .append("' OR t.original_text LIKE ? ESCAPE '").append(SqlLike.ESCAPE)
                .append("' OR t.note LIKE ? ESCAPE '").append(SqlLike.ESCAPE).append("') ");
            String kw = SqlLike.contains(query.getKeyword());
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (query.getMinAmount() != null) {
            sql.append("AND t.amount >= ? ");
            params.add(query.getMinAmount());
        }
        if (query.getMaxAmount() != null) {
            sql.append("AND t.amount <= ? ");
            params.add(query.getMaxAmount());
        }
    }
}
