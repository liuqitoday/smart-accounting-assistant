package com.liuqitech.accountingassistant.repository;

import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.projection.AnalysisNamedOption;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByType(TransactionType type);

    List<Category> findByTypeAndLevel(TransactionType type, Integer level);

    List<Category> findByLevel(Integer level);

    @Query("SELECT c FROM Category c WHERE c.level = 1 AND c.type = :type ORDER BY c.id")
    List<Category> findTopLevelCategoriesByType(@Param("type") TransactionType type);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId")
    List<Category> findByParentId(@Param("parentId") Long parentId);

    List<Category> findByNameAndType(String name, TransactionType type);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = :id")
    Optional<Category> findByIdWithParent(@Param("id") Long id);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.id = :id")
    Optional<Category> findByIdWithChildren(@Param("id") Long id);

    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.children WHERE c.level = 1 ORDER BY c.type, c.id")
    List<Category> findAllLevel1WithChildren();

    /**
     * 分析 prompt 用的轻量分类选项（全局分类，一级优先）。
     */
    @Query("SELECT c.id AS id, c.name AS name FROM Category c ORDER BY c.level ASC, c.id ASC")
    List<AnalysisNamedOption> findAnalysisOptions(Pageable pageable);
}
