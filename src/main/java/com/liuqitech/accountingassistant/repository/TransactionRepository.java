package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易记录数据访问接口
 * 继承 JpaSpecificationExecutor 支持动态查询
 *
 * 注意：常规的 CRUD 和筛选查询使用 Specification 动态构建
 * 本接口只保留特殊用途的查询方法（统计、聚合、原生 SQL 等）。
 * 数据隔离维度为账本（ledger_id）；created_by 仅作为「谁录入」的审计字段。
 *
 * 原生 SQL 中 transaction_date 列恒为 YYYY-MM-DD 文本（LocalDateStringConverter 写入，
 * 启动时 SchemaIndexInitializer 归一历史数据），WHERE 侧直接比较列即可命中
 * idx_tx_ledger_date 索引——禁止对列包 date()（空转且毁索引）；参数侧 date() 无妨。
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    // ==================== 基础查询方法（保留用于简单场景） ====================

    /**
     * 查询账本所有交易记录并 JOIN FETCH tags（用于 CSV 导出，避免遍历 tags 时逐条懒加载 N+1，排除软删除）
     */
    @Query("SELECT DISTINCT t FROM Transaction t LEFT JOIN FETCH t.tags WHERE t.ledgerId = :ledgerId AND t.deletedAt IS NULL ORDER BY t.id DESC")
    List<Transaction> findAllWithTagsByLedgerIdOrderByIdDesc(@Param("ledgerId") Long ledgerId);

    /**
     * 获取账本最近5条交易记录（排除软删除）
     */
    @Query(value = "SELECT * FROM transactions WHERE ledger_id = :ledgerId AND deleted_at IS NULL ORDER BY transaction_date DESC, id DESC LIMIT 5", nativeQuery = true)
    List<Transaction> findTop5ByLedgerIdOrderByTransactionDateDescIdDesc(@Param("ledgerId") Long ledgerId);

    /**
     * 获取账本指定日期范围内最大 5 笔支出（按金额降序，供统计页大额支出排行，排除软删除）
     */
    @Query(value = "SELECT * FROM transactions WHERE ledger_id = :ledgerId AND type = :type AND transaction_date BETWEEN :startDate AND :endDate AND deleted_at IS NULL ORDER BY amount DESC LIMIT 5", nativeQuery = true)
    List<Transaction> findTop5ByLedgerIdAndTypeAndTransactionDateBetweenOrderByAmountDesc(
            @Param("ledgerId") Long ledgerId, @Param("type") String type, @Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 删除账本时清理其全部交易
     */
    void deleteByLedgerId(Long ledgerId);

    boolean existsByRecurringBillIdAndRecurringOccurrenceDate(Long recurringBillId, LocalDate recurringOccurrenceDate);

    // ==================== 统计和聚合查询 ====================
    /**
     * 一次性统计账本内各账户「作为转入方」的转账合计与笔数，供账户余额计算（转入额为正，排除软删除）。
     * 返回行：[counterAccountId, sumAmount, count]
     */
    @Query("SELECT t.counterAccountId, COALESCE(SUM(t.amount), 0), COUNT(t) FROM Transaction t " +
            "WHERE t.ledgerId = :ledgerId AND t.type = 'TRANSFER' AND t.counterAccountId IS NOT NULL AND t.deletedAt IS NULL " +
            "GROUP BY t.counterAccountId")
    List<Object[]> sumTransferInByAccount(@Param("ledgerId") Long ledgerId);

    /**
     * 统计单个账户「作为转入方」的转账合计与笔数（排除软删除）。返回单行：[sumAmount, count]
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0), COUNT(t) FROM Transaction t " +
            "WHERE t.counterAccountId = :accountId AND t.type = 'TRANSFER' AND t.deletedAt IS NULL")
    List<Object[]> sumTransferInByAccountSingle(@Param("accountId") Long accountId);

    /**
     * 统计以该账户为转入方的转账笔数（删除账户前的引用校验，排除软删除）
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.ledgerId = :ledgerId AND t.counterAccountId = :counterAccountId AND t.deletedAt IS NULL")
    long countByLedgerIdAndCounterAccountId(@Param("ledgerId") Long ledgerId, @Param("counterAccountId") Long counterAccountId);

    /**
     * 一次性统计账本内各账户的收支净额与笔数，供账户当前余额计算（避免逐账户 N+1，排除软删除）。
     * 返回行：[accountId, type, sumAmount, count]，余额 = 期初 + Σ收入 − Σ支出。
     */
    @Query("SELECT t.accountId, t.type, COALESCE(SUM(t.amount), 0), COUNT(t) FROM Transaction t " +
            "WHERE t.ledgerId = :ledgerId AND t.accountId IS NOT NULL AND t.deletedAt IS NULL " +
            "GROUP BY t.accountId, t.type")
    List<Object[]> sumAmountByAccountAndType(@Param("ledgerId") Long ledgerId);

    /**
     * 统计单个账户按类型的收支合计与笔数（单账户余额计算，排除软删除）。
     * 返回行：[type, sumAmount, count]
     */
    @Query("SELECT t.type, COALESCE(SUM(t.amount), 0), COUNT(t) FROM Transaction t " +
            "WHERE t.accountId = :accountId AND t.deletedAt IS NULL GROUP BY t.type")
    List<Object[]> sumAmountByAccountGroupByType(@Param("accountId") Long accountId);

    /**
     * 统计关联指定账户的交易笔数（删除账户前的引用校验，排除软删除）
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.ledgerId = :ledgerId AND t.accountId = :accountId AND t.deletedAt IS NULL")
    long countByLedgerIdAndAccountId(@Param("ledgerId") Long ledgerId, @Param("accountId") Long accountId);
}
