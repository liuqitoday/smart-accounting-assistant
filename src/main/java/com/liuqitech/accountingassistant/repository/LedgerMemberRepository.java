package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.LedgerMember;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
