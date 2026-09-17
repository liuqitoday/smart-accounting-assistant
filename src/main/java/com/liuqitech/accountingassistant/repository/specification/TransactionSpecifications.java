package com.liuqitech.accountingassistant.repository.specification;

import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.util.SqlLike;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 交易记录查询条件构建器
 * 使用 JPA Specification 实现动态查询
 */
public class TransactionSpecifications {

    /**
     * 构建交易查询条件
     *
     * @param ledgerId   账本ID（必需，数据隔离维度）
     * @param keyword    关键词搜索（可选，模糊匹配描述、商户、备注）
     * @param type       交易类型（可选）
     * @param startDate  开始日期（可选）
     * @param endDate    结束日期（可选）
     * @param tagIds     标签ID列表（可选）
     * @param createdBy  创建人用户名（可选，精确匹配）
     * @param categoryId 分类ID（可选，支持一级类目或二级类目）
     * @param accountId  账户ID（可选，精确匹配交易 accountId）
     * @param merchant   商家（可选，模糊匹配 parsedMerchant）
     * @param minAmount  最小金额（可选，含）
     * @param maxAmount  最大金额（可选，含）
     * @return Specification
     */
    public static Specification<Transaction> buildQuery(
            Long ledgerId,
            String keyword,
            TransactionType type,
            LocalDate startDate,
            LocalDate endDate,
            List<Long> tagIds,
            String createdBy,
            Long categoryId,
            Long accountId,
            String merchant,
            BigDecimal minAmount,
            BigDecimal maxAmount) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. 账本过滤（必需）
            if (ledgerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("ledgerId"), ledgerId));
            }

            // 2. 关键词搜索（模糊匹配：描述、原始文本、商户名、备注）
            if (keyword != null && !keyword.isBlank()) {
                String pattern = SqlLike.contains(keyword.toLowerCase());
                Predicate descriptionMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("description")), pattern, SqlLike.ESCAPE);
                Predicate originalTextMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("originalText")), pattern, SqlLike.ESCAPE);
                Predicate merchantMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("parsedMerchant")), pattern, SqlLike.ESCAPE);
                Predicate noteMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("note")), pattern, SqlLike.ESCAPE);
                predicates.add(criteriaBuilder.or(descriptionMatch, originalTextMatch, merchantMatch, noteMatch));
            }

            // 3. 交易类型过滤
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }

            // 4. 日期范围过滤
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }

            // 5. 标签过滤
            if (tagIds != null && !tagIds.isEmpty()) {
                Join<Transaction, Tag> tagJoin = root.join("tags", JoinType.INNER);
                predicates.add(tagJoin.get("id").in(tagIds));

                // 去重（同一交易可能有多个标签）
                query.distinct(true);
            }

            // 6. 创建人过滤（精确匹配用户名）
            if (createdBy != null && !createdBy.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), createdBy));
            }

            // 7. 分类过滤（支持一级类目或二级类目）
            if (categoryId != null) {
                // 匹配二级类目（categoryId）或一级类目（parentCategoryId）
                Predicate categoryMatch = criteriaBuilder.equal(root.get("categoryId"), categoryId);
                Predicate parentCategoryMatch = criteriaBuilder.equal(root.get("parentCategoryId"), categoryId);
                predicates.add(criteriaBuilder.or(categoryMatch, parentCategoryMatch));
            }

            // 8. 账户过滤（精确匹配交易账户）
            if (accountId != null) {
                predicates.add(criteriaBuilder.equal(root.get("accountId"), accountId));
            }

            // 9. 商家过滤（模糊匹配 parsedMerchant）
            if (merchant != null && !merchant.isBlank()) {
                String merchantPattern = SqlLike.contains(merchant.toLowerCase());
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("parsedMerchant")), merchantPattern, SqlLike.ESCAPE));
            }

            // 10. 金额范围过滤
            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }

            // 11. 排除软删除的记录
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 按账本查询（排除软删除）
     */
    public static Specification<Transaction> byLedger(Long ledgerId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("ledgerId"), ledgerId),
                criteriaBuilder.isNull(root.get("deletedAt"))
        );
    }

    /**
     * 按交易类型查询
     */
    public static Specification<Transaction> byType(TransactionType type) {
        return (root, query, criteriaBuilder) ->
                type == null ? null : criteriaBuilder.equal(root.get("type"), type);
    }

    /**
     * 按日期范围查询
     */
    public static Specification<Transaction> byDateRange(LocalDate startDate, LocalDate endDate) {
        return (root, query, criteriaBuilder) -> {
            if (startDate == null && endDate == null) {
                return null;
            }

            List<Predicate> predicates = new ArrayList<>();

            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 按标签查询
     */
    public static Specification<Transaction> byTags(List<Long> tagIds) {
        return (root, query, criteriaBuilder) -> {
            if (tagIds == null || tagIds.isEmpty()) {
                return null;
            }

            Join<Transaction, Tag> tagJoin = root.join("tags", JoinType.INNER);
            query.distinct(true);
            return tagJoin.get("id").in(tagIds);
        };
    }

    /**
     * 按ID降序排序
     */
    public static Specification<Transaction> orderByIdDesc() {
        return (root, query, criteriaBuilder) -> {
            query.orderBy(criteriaBuilder.desc(root.get("id")));
            return null;
        };
    }
}
