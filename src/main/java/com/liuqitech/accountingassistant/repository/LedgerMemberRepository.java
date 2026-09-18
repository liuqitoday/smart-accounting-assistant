package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.LedgerMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 账本成员数据访问接口
 */
@Repository
public interface LedgerMemberRepository extends JpaRepository<LedgerMember, Long> {

    /** 查某用户在某账本中的成员身份（成员校验、角色判断用） */
    Optional<LedgerMember> findByLedgerIdAndUsername(Long ledgerId, String username);

    /** 某用户加入的全部账本（我的账本列表用） */
    List<LedgerMember> findByUsername(String username);

    /** 某账本的全部成员 */
    List<LedgerMember> findByLedgerId(Long ledgerId);

    /** 某账本成员数 */
    long countByLedgerId(Long ledgerId);

    /** 批量统计多个账本的成员数（避免逐账本 N+1）。返回 [ledgerId, count] 行。 */
    @Query("SELECT m.ledgerId, COUNT(m) FROM LedgerMember m WHERE m.ledgerId IN :ids GROUP BY m.ledgerId")
    List<Object[]> countByLedgerIdIn(@Param("ids") List<Long> ids);

    /** 删除账本时清理成员行 */
    void deleteByLedgerId(Long ledgerId);

    /** 是否为某账本成员 */
    boolean existsByLedgerIdAndUsername(Long ledgerId, String username);

    /**
     * 清掉该账本内所有成员指向某账户的默认账户设置（账户被停用或删除时调用）。
     * 默认账户的前提是账户在用，因此账户一旦离场就要把引用一并抹掉，避免留下不生效的偏好
     * （也避免 SQLite 复用已删除的 id 时被误认作默认账户）。
     *
     * <p>批量 UPDATE 不经过持久化上下文，故显式 flush 待写变更并清空一级缓存，
     * 否则同事务内后续读取仍会拿到已被清空的旧 LedgerMember。</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE LedgerMember m SET m.defaultAccountId = NULL "
         + "WHERE m.ledgerId = :ledgerId AND m.defaultAccountId = :accountId")
    int clearDefaultAccount(@Param("ledgerId") Long ledgerId, @Param("accountId") Long accountId);
}
