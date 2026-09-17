package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.repository.projection.AnalysisNamedOption;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 账户数据访问接口（数据隔离维度为账本 ledger_id）
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * 查询账本的所有账户（在用优先，其次按 id 升序）
     */
    List<Account> findByLedgerIdOrderByActiveDescIdAsc(Long ledgerId);

    /**
     * 账本内是否存在指定账户
     */
    boolean existsByIdAndLedgerId(Long id, Long ledgerId);

    /**
     * 账本内是否存在同名账户
     */
    boolean existsByNameAndLedgerId(String name, Long ledgerId);

    /**
     * 按名称与账本查找账户（CSV 导入按名匹配复用）
     */
    Optional<Account> findByNameAndLedgerId(String name, Long ledgerId);

    /**
     * 删除账本时清理其全部账户
     */
    void deleteByLedgerId(Long ledgerId);

    /**
     * 分析 prompt 用的轻量账户选项（id+name，不计算余额）。
     */
    @Query("SELECT a.id AS id, a.name AS name FROM Account a "
         + "WHERE a.ledgerId = :ledgerId ORDER BY a.active DESC, a.id ASC")
    List<AnalysisNamedOption> findAnalysisOptionsByLedgerId(@Param("ledgerId") Long ledgerId,
                                                            Pageable pageable);
}
