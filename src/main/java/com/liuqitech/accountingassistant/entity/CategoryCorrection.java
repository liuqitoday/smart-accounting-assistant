package com.liuqitech.accountingassistant.entity;

import com.liuqitech.accountingassistant.enums.TransactionType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 分类纠正记忆实体
 *
 * <p>记录用户在主流程中手动改掉 AI 初次分类的行为：当用户保存的分类与 AI 建议不一致时，
 * 把「匹配键（商户/描述） → 用户选择的分类」按用户存下来。下次解析相同商户时直接套用，
 * 让记账助手越用越聪明。</p>
 */
@Entity
@Table(
        name = "category_correction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_correction_ledger_type_key",
                columnNames = {"ledger_id", "type", "match_type", "match_key"}
        )
)
public class CategoryCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属账本（成员共享记忆，隔离维度） */
    @Column(name = "ledger_id")
    private Long ledgerId;

    /** 最后记录/修改该纠正的用户（审计用，不再作为隔离键） */
    @Column(nullable = false, length = 50)
    private String username;

    /** 匹配键类型：MERCHANT（商户，优先）/ DESC（描述，退化） */
    @Column(name = "match_type", nullable = false, length = 20)
    private String matchType;

    /** 规范化后的匹配键（商户名或描述） */
    @Column(name = "match_key", nullable = false, length = 200)
    private String matchKey;

    /** 交易类型，收/支分开记忆 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    /** 用户纠正后选择的分类 ID */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /** 纠正后分类名称（冗余，便于排查） */
    @Column(name = "category_name", length = 100)
    private String categoryName;

    /** AI 当初错给的分类 ID（供调试/后续分析） */
    @Column(name = "ai_suggested_category_id")
    private Long aiSuggestedCategoryId;

    /** 触发本条纠正的原始输入，留样 */
    @Column(name = "sample_text", columnDefinition = "TEXT")
    private String sampleText;

    /** 命中次数（被复用的次数，供后续加权） */
    @Column(name = "hit_count", nullable = false)
    private Integer hitCount = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CategoryCorrection() {}

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLedgerId() {
        return ledgerId;
    }

    public void setLedgerId(Long ledgerId) {
        this.ledgerId = ledgerId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public String getMatchKey() {
        return matchKey;
    }

    public void setMatchKey(String matchKey) {
        this.matchKey = matchKey;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getAiSuggestedCategoryId() {
        return aiSuggestedCategoryId;
    }

    public void setAiSuggestedCategoryId(Long aiSuggestedCategoryId) {
        this.aiSuggestedCategoryId = aiSuggestedCategoryId;
    }

    public String getSampleText() {
        return sampleText;
    }

    public void setSampleText(String sampleText) {
        this.sampleText = sampleText;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "CategoryCorrection{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", matchType='" + matchType + '\'' +
                ", matchKey='" + matchKey + '\'' +
                ", type=" + type +
                ", categoryId=" + categoryId +
                ", categoryName='" + categoryName + '\'' +
                ", hitCount=" + hitCount +
                '}';
    }
}
