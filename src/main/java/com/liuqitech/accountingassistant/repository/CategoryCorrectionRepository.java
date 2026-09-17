package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.CategoryCorrection;
import com.liuqitech.accountingassistant.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 分类纠正记忆数据访问接口
 */
@Repository
public interface CategoryCorrectionRepository extends JpaRepository<CategoryCorrection, Long> {

    /**
     * 按账本 + 类型 + 匹配键类型 + 匹配键 查询纠正记忆（用于 upsert 与命中查询）
     */
    Optional<CategoryCorrection> findByLedgerIdAndTypeAndMatchTypeAndMatchKey(
            Long ledgerId, TransactionType type, String matchType, String matchKey);

    /**
     * 删除账本时清理其纠正记忆
     */
    void deleteByLedgerId(Long ledgerId);
}
