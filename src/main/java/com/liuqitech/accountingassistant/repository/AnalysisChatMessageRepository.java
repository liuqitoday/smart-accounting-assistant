package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.AnalysisChatMessage;
import com.liuqitech.accountingassistant.repository.projection.AnalysisConversationLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalysisChatMessageRepository extends JpaRepository<AnalysisChatMessage, Long> {

    /**
     * 查询最近 N 条消息（含 USER + ASSISTANT，status=OK 的），倒序（最新在前）
     */
    @Query("SELECT m FROM AnalysisChatMessage m WHERE m.ledgerId = :ledgerId AND m.userId = :userId " +
           "AND m.status = 'OK' ORDER BY m.id DESC")
    List<AnalysisChatMessage> findRecentMessages(@Param("ledgerId") Long ledgerId,
                                                  @Param("userId") String userId,
                                                  Pageable pageable);

    /**
     * 分页查询历史消息（倒序：最新在前）
     */
    @Query("SELECT m FROM AnalysisChatMessage m WHERE m.ledgerId = :ledgerId AND m.userId = :userId " +
           "ORDER BY m.id DESC")
    Page<AnalysisChatMessage> findByLedgerIdAndUserId(@Param("ledgerId") Long ledgerId,
                                                       @Param("userId") String userId,
                                                       Pageable pageable);

    /**
     * 统计当日该用户的 USER 消息数（跨账本，用于限流）。
     * 用 createdAt 区间而非 DATE() 函数：JPQL 的 DATE() 在 H2/SQLite 方言间不可移植。
     * 仅计 status=OK 的 USER 消息（失败回合不占配额，避免上游不稳时惩罚用户与重试）。
     */
    @Query("SELECT COUNT(m) FROM AnalysisChatMessage m WHERE m.userId = :userId AND m.role = 'USER' " +
           "AND m.status = 'OK' AND m.createdAt >= :dayStart AND m.createdAt < :dayEnd")
    long countUserMessagesToday(@Param("userId") String userId,
                                @Param("dayStart") LocalDateTime dayStart,
                                @Param("dayEnd") LocalDateTime dayEnd);

    /**
     * 清空指定账本+用户的全部消息
     */
    void deleteByLedgerIdAndUserId(Long ledgerId, String userId);

    /**
     * 删除账本时级联清理该账本全部成员的聊天流（隐私：payload 含明细数据，不得残留）
     */
    void deleteByLedgerId(Long ledgerId);

    /**
     * 最近对话投影：只取 role/content，不加载 payload。status=OK，按 id 倒序。
     */
    @Query("SELECT m.role AS role, m.content AS content FROM AnalysisChatMessage m "
         + "WHERE m.ledgerId = :ledgerId AND m.userId = :userId AND m.status = 'OK' "
         + "ORDER BY m.id DESC")
    List<AnalysisConversationLine> findRecentConversationLines(@Param("ledgerId") Long ledgerId,
                                                               @Param("userId") String userId,
                                                               Pageable pageable);
}
