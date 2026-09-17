package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 交易解析响应DTO
 */
public class TransactionParseResponse {
    
    private Long id;
    private BigDecimal amount;
    private TransactionType type;
    private String description;
    private String originalText;
    private Long categoryId;
    private String categoryName;
    private Long parentCategoryId;
    private String parentCategoryName;
    private LocalDate transactionDate;
    private String parsedMerchant;
    private BigDecimal confidenceScore;
    private String aiModelUsed;
    private String note;
    private String relatedUser;
    /** AI（或记忆）初次建议的分类 ID：解析时由后端写入，保存时原样带回用于对比，判断用户是否手动改了分类 */
    private Long aiSuggestedCategoryId;
    /** 本次分类是否来自历史纠正记忆（命中用户习惯） */
    private Boolean learnedFromHistory;
    /** AI 解析失败后是否使用了基础规则降级解析 */
    private boolean fallbackUsed;
    /** 交易关联的标签列表 */
    private List<TagDto> tags;
    /** 关联账户 ID（可选） */
    private Long accountId;

    // 默认构造函数
    public TransactionParseResponse() {}
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public TransactionType getType() {
        return type;
    }
    
    public void setType(TransactionType type) {
        this.type = type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getOriginalText() {
        return originalText;
    }
    
    public void setOriginalText(String originalText) {
        this.originalText = originalText;
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

    public Long getParentCategoryId() {
        return parentCategoryId;
    }

    public void setParentCategoryId(Long parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
    }

    public String getParentCategoryName() {
        return parentCategoryName;
    }

    public void setParentCategoryName(String parentCategoryName) {
        this.parentCategoryName = parentCategoryName;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }
    
    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }
    
    public String getParsedMerchant() {
        return parsedMerchant;
    }
    
    public void setParsedMerchant(String parsedMerchant) {
        this.parsedMerchant = parsedMerchant;
    }
    
    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }
    
    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
    
    public String getAiModelUsed() {
        return aiModelUsed;
    }
    
    public void setAiModelUsed(String aiModelUsed) {
        this.aiModelUsed = aiModelUsed;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getRelatedUser() {
        return relatedUser;
    }

    public void setRelatedUser(String relatedUser) {
        this.relatedUser = relatedUser;
    }

    public Long getAiSuggestedCategoryId() {
        return aiSuggestedCategoryId;
    }

    public void setAiSuggestedCategoryId(Long aiSuggestedCategoryId) {
        this.aiSuggestedCategoryId = aiSuggestedCategoryId;
    }

    public Boolean getLearnedFromHistory() {
        return learnedFromHistory;
    }

    public void setLearnedFromHistory(Boolean learnedFromHistory) {
        this.learnedFromHistory = learnedFromHistory;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public List<TagDto> getTags() {
        return tags;
    }

    public void setTags(List<TagDto> tags) {
        this.tags = tags;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    @Override
    public String toString() {
        return "TransactionParseResponse{" +
                "id=" + id +
                ", amount=" + amount +
                ", type=" + type +
                ", description='" + description + '\'' +
                ", categoryName='" + categoryName + '\'' +
                ", transactionDate=" + transactionDate +
                ", confidenceScore=" + confidenceScore +
                '}';
    }
}
