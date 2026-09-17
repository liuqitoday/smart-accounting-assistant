package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.CategoryCorrection;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.CategoryCorrectionRepository;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 分类纠正记忆服务
 *
 * <p>让记账助手「越用越聪明」的第一阶段：确定性商户记忆。</p>
 * <ul>
 *     <li>捕获：用户在主流程把 AI 初次分类改成别的并保存时，记下「商户/描述 → 用户分类」。</li>
 *     <li>应用：下次解析相同商户时，直接返回记住的分类，覆盖 AI 结果。</li>
 * </ul>
 * 记忆按用户、按收/支类型隔离。
 */
@Service
public class CategoryCorrectionService {

    private static final Logger logger = LoggerFactory.getLogger(CategoryCorrectionService.class);

    /** 匹配键最大长度，与实体列定义保持一致 */
    private static final int MAX_KEY_LENGTH = 200;

    private static final String MATCH_TYPE_MERCHANT = "MERCHANT";
    private static final String MATCH_TYPE_DESC = "DESC";

    private final CategoryCorrectionRepository correctionRepository;
    private final CategoryRepository categoryRepository;

    public CategoryCorrectionService(CategoryCorrectionRepository correctionRepository,
                                     CategoryRepository categoryRepository) {
        this.correctionRepository = correctionRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * 记录一条分类纠正（捕获）。仅在调用方已确认「用户改了分类」时调用。
     * 以 (ledgerId, type, matchType, matchKey) 为唯一键做 upsert：已存在则更新为最新分类（最近优先），否则新增。
     * username 仅作为「最后修改者」审计字段记录。
     */
    @Transactional
    public void recordCorrection(Long ledgerId, String username, TransactionType type, String merchant, String description,
                                 String originalText, Long userCategoryId, String userCategoryName,
                                 Long aiSuggestedCategoryId) {
        if (ledgerId == null || type == null || userCategoryId == null) {
            return;
        }

        MatchTarget target = resolveMatchTarget(merchant, description);
        if (target == null) {
            logger.debug("无可用匹配键，跳过纠正记忆: merchant={}, description={}", merchant, description);
            return;
        }

        CategoryCorrection correction = correctionRepository
                .findByLedgerIdAndTypeAndMatchTypeAndMatchKey(ledgerId, type, target.matchType, target.matchKey)
                .orElseGet(CategoryCorrection::new);

        boolean isNew = correction.getId() == null;
        if (isNew) {
            correction.setLedgerId(ledgerId);
            correction.setType(type);
            correction.setMatchType(target.matchType);
            correction.setMatchKey(target.matchKey);
            correction.setHitCount(0);
        }
        correction.setUsername(username);
        correction.setCategoryId(userCategoryId);
        correction.setCategoryName(userCategoryName);
        correction.setAiSuggestedCategoryId(aiSuggestedCategoryId);
        correction.setSampleText(originalText);

        correctionRepository.save(correction);
        logger.info("{}分类纠正记忆: ledger={}, user={}, {}=[{}], 分类={}({}), AI原建议={}",
                isNew ? "新增" : "更新", ledgerId, username, target.matchType, target.matchKey,
                userCategoryName, userCategoryId, aiSuggestedCategoryId);
    }

    /**
     * 查询命中的纠正分类（应用）。命中则累计复用次数并返回对应分类实体。
     */
    @Transactional
    public Optional<Category> findCorrectedCategory(Long ledgerId, TransactionType type,
                                                    String merchant, String description) {
        if (ledgerId == null || type == null) {
            return Optional.empty();
        }

        MatchTarget target = resolveMatchTarget(merchant, description);
        if (target == null) {
            return Optional.empty();
        }

        Optional<CategoryCorrection> correctionOpt = correctionRepository
                .findByLedgerIdAndTypeAndMatchTypeAndMatchKey(ledgerId, type, target.matchType, target.matchKey);
        if (correctionOpt.isEmpty()) {
            return Optional.empty();
        }

        CategoryCorrection correction = correctionOpt.get();
        Optional<Category> categoryOpt = categoryRepository.findById(correction.getCategoryId());
        if (categoryOpt.isEmpty()) {
            logger.warn("纠正记忆指向的分类已不存在，忽略: correctionId={}, categoryId={}",
                    correction.getId(), correction.getCategoryId());
            return Optional.empty();
        }

        correction.setHitCount((correction.getHitCount() == null ? 0 : correction.getHitCount()) + 1);
        correctionRepository.save(correction);

        logger.info("命中分类纠正记忆: ledger={}, {}=[{}] -> 分类={}({})", ledgerId, target.matchType, target.matchKey,
                correction.getCategoryName(), correction.getCategoryId());
        return categoryOpt;
    }

    /**
     * 解析匹配键：优先商户，其次描述。两者都为空则返回 null（不记忆/不命中）。
     */
    private MatchTarget resolveMatchTarget(String merchant, String description) {
        String merchantKey = normalizeKey(merchant);
        if (merchantKey != null) {
            return new MatchTarget(MATCH_TYPE_MERCHANT, merchantKey);
        }
        String descKey = normalizeKey(description);
        if (descKey != null) {
            return new MatchTarget(MATCH_TYPE_DESC, descKey);
        }
        return null;
    }

    /**
     * 规范化匹配键：去首尾空白、转小写、去除内部空白，并截断到最大长度。空字符串返回 null。
     */
    private String normalizeKey(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase().replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_KEY_LENGTH) {
            normalized = normalized.substring(0, MAX_KEY_LENGTH);
        }
        return normalized;
    }

    /**
     * 匹配键 = 键类型 + 规范化后的键值
     */
    private static class MatchTarget {
        final String matchType;
        final String matchKey;

        MatchTarget(String matchType, String matchKey) {
            this.matchType = matchType;
            this.matchKey = matchKey;
        }
    }
}
