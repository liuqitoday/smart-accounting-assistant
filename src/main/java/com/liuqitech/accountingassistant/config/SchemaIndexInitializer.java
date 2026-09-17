package com.liuqitech.accountingassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等地创建 SQLite 所需的唯一索引、查询索引，以及 Hibernate 不管理的 Remember-Me 令牌表。
 *
 * <p>Hibernate 的 SQLite 方言不会根据实体上的 {@code @UniqueConstraint} 自动创建唯一索引，
 * 所以在此手动补齐，确保账本维度的唯一性约束在数据库层面也生效。</p>
 *
 * <p>所有 SQL 均幂等（{@code IF NOT EXISTS} / {@code DROP INDEX IF EXISTS} / 条件 UPDATE），可安全重复运行。</p>
 */
@Component
@Order(100)
public class SchemaIndexInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SchemaIndexInitializer.class);

    private final JdbcTemplate jdbc;

    public SchemaIndexInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        createPersistentLoginsTable();
        createUniqueIndexes();
        logger.debug("数据库索引初始化完成");
    }

    /**
     * Spring Security 持久化 Remember-Me 令牌表（JdbcTokenRepositoryImpl 标准 schema）。
     * 不放 data.sql：测试环境 sql.init.mode=never 不执行 data.sql，而本 Runner 两端都跑。
     */
    private void createPersistentLoginsTable() {
        safeExec("CREATE TABLE IF NOT EXISTS persistent_logins ("
                + "username VARCHAR(64) NOT NULL, "
                + "series VARCHAR(64) PRIMARY KEY, "
                + "token VARCHAR(64) NOT NULL, "
                + "last_used TIMESTAMP NOT NULL)");
    }

    private void createUniqueIndexes() {
        safeExec("CREATE UNIQUE INDEX IF NOT EXISTS uk_member_ledger_user ON ledger_members(ledger_id, username)");
        safeExec("CREATE UNIQUE INDEX IF NOT EXISTS uk_tag_name_ledger ON tags(name, ledger_id)");
        safeExec("CREATE UNIQUE INDEX IF NOT EXISTS uk_correction_ledger_type_key "
                + "ON category_correction(ledger_id, type, match_type, match_key)");
        safeExec("CREATE UNIQUE INDEX IF NOT EXISTS uk_account_name_ledger ON accounts(name, ledger_id)");
        safeExec("CREATE INDEX IF NOT EXISTS idx_tx_account ON transactions(account_id)");
        // 主访问路径复合索引：列表默认按 transactionDate,desc 排序、统计按 ledger+日期范围过滤，
        // (ledger_id, transaction_date DESC, id DESC) 可同时覆盖过滤与排序（实测比无索引快 60-90 倍）
        safeExec("CREATE INDEX IF NOT EXISTS idx_tx_ledger_date "
                + "ON transactions(ledger_id, transaction_date DESC, id DESC)");
        // deleted_at 单列索引经 EXPLAIN 实测为负优化（deleted_at IS NULL 命中几乎全表，随机回表比顺序扫更差），
        // 主动 DROP 使已部署库同样收敛
        safeExec("DROP INDEX IF EXISTS idx_tx_deleted_at");
        // 历史兜底：LocalDateStringConverter 引入（847710e）前的旧行可能带时间后缀（读侧 substring(0,10) 即为此防御）；
        // 归一为 YYYY-MM-DD 后，查询侧才能安全地对列做裸比较（不再套 date()）并命中上面的复合索引
        safeExec("UPDATE transactions SET transaction_date = substr(transaction_date, 1, 10) "
                + "WHERE length(transaction_date) > 10");
        safeExec("CREATE UNIQUE INDEX IF NOT EXISTS uk_tx_recurring_occurrence "
                + "ON transactions(recurring_bill_id, recurring_occurrence_date)");
        safeExec("CREATE INDEX IF NOT EXISTS idx_tx_recurring_bill ON transactions(recurring_bill_id)");
        safeExec("CREATE INDEX IF NOT EXISTS idx_recurring_bills_due "
                + "ON recurring_bills(enabled, deleted_at, next_run_date)");
        safeExec("CREATE UNIQUE INDEX IF NOT EXISTS uk_recurring_bill_tag "
                + "ON recurring_bill_tags(recurring_bill_id, tag_id)");
        safeExec("CREATE INDEX IF NOT EXISTS idx_analysis_chat_ledger_user_id " +
                "ON analysis_chat_messages(ledger_id, user_id, id)");
        safeExec("CREATE UNIQUE INDEX IF NOT EXISTS uk_analysis_context_ledger_user "
                + "ON analysis_chat_contexts(ledger_id, user_id)");
        safeExec("CREATE UNIQUE INDEX IF NOT EXISTS uk_analysis_usage_daily_user_date "
                + "ON analysis_usage_daily(user_id, business_date)");
        safeExec("CREATE INDEX IF NOT EXISTS idx_analysis_usage_event_user_created "
                + "ON analysis_usage_events(user_id, created_at)");
        safeExec("CREATE INDEX IF NOT EXISTS idx_transaction_tags_tag_transaction "
                + "ON transaction_tags(tag_id, transaction_id)");
    }

    private void safeExec(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            logger.warn("Schema SQL 执行失败(忽略): {} - {}", sql, e.getMessage());
        }
    }
}
