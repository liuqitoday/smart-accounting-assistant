package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.repository.projection.AnalysisNamedOption;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 标签数据访问接口
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * 查询账本的所有标签（成员共享）
     */
    List<Tag> findByLedgerIdOrderByNameAsc(Long ledgerId);

    /**
     * 根据名称和账本查找标签
     */
    Optional<Tag> findByNameAndLedgerId(String name, Long ledgerId);

    /**
     * 检查账本内标签名称是否存在
     */
    boolean existsByNameAndLedgerId(String name, Long ledgerId);

    /**
     * 删除账本时清理其全部标签
     */
    void deleteByLedgerId(Long ledgerId);

    /**
     * 查询所有系统预置标签
     */
    List<Tag> findBySystemTrueOrderByNameAsc();

    /**
     * 是否存在同名系统预置标签（用户标签查重用，忽略大小写）
     */
    boolean existsByNameIgnoreCaseAndSystemTrue(String name);

    /**
     * 按名称查找系统预置标签（导入时复用，忽略大小写）
     */
    Optional<Tag> findByNameIgnoreCaseAndSystemTrue(String name);

    /**
     * 分析 prompt 用的轻量标签选项：系统标签或当前账本标签。
     */
    @Query("SELECT t.id AS id, t.name AS name FROM Tag t "
         + "WHERE t.system = true OR t.ledgerId = :ledgerId "
         + "ORDER BY t.system DESC, t.name ASC")
    List<AnalysisNamedOption> findAnalysisOptions(@Param("ledgerId") Long ledgerId, Pageable pageable);
}
