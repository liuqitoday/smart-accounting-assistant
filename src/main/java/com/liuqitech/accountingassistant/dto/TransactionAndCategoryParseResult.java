package com.liuqitech.accountingassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liuqitech.accountingassistant.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 合并解析结果 DTO：一次调用同时承载交易抽取 + 分类选择结果。
 *
 * <p>用于将原来「抽取」和「分类」两次 AI 调用合并为一次，省掉一次网络往返。</p>
 */
public class TransactionAndCategoryParseResult {

    // ===== 抽取字段 =====
    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("type")
    private TransactionType type;

    @JsonProperty("merchant")
    private String merchant;

    @JsonProperty("description")
    private String description;

    @JsonProperty("transactionDate")
    private LocalDate transactionDate;

    @JsonProperty("note")
    private String note;

    @JsonProperty("relatedUser")
    private String relatedUser;

    /** 抽取置信度（0-1） */
    @JsonProperty("confidence")
    private BigDecimal confidence;

    // ===== 分类字段 =====
    @JsonProperty("categoryId")
    private Long categoryId;

    @JsonProperty("categoryName")
    private String categoryName;

    @JsonProperty("parentCategoryId")
    private Long parentCategoryId;

    @JsonProperty("parentCategoryName")
    private String parentCategoryName;

    /** 分类置信度（0-1），与抽取置信度区分命名 */
    @JsonProperty("categoryConfidence")
    private Double categoryConfidence;

    @JsonProperty("reason")
    private String reason;

    public TransactionAndCategoryParseResult() {}

    /**
     * 从纯抽取结果构造（分类字段留空），供 fallback 路径复用：
     * AI 合并调用失败时，用规则抽取结果填充抽取字段，分类由调用方另行解析。
     */
    public TransactionAndCategoryParseResult(TransactionParseResult extraction) {
        this.amount = extraction.getAmount();
        this.type = extraction.getType();
        this.merchant = extraction.getMerchant();
        this.description = extraction.getDescription();
        this.transactionDate = extraction.getTransactionDate();
        this.note = extraction.getNote();
        this.relatedUser = extraction.getRelatedUser();
        this.confidence = extraction.getConfidence();
    }

    // ===== Getters and Setters =====
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getRelatedUser() { return relatedUser; }
    public void setRelatedUser(String relatedUser) { this.relatedUser = relatedUser; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Long getParentCategoryId() { return parentCategoryId; }
    public void setParentCategoryId(Long parentCategoryId) { this.parentCategoryId = parentCategoryId; }

    public String getParentCategoryName() { return parentCategoryName; }
    public void setParentCategoryName(String parentCategoryName) { this.parentCategoryName = parentCategoryName; }

    public Double getCategoryConfidence() { return categoryConfidence; }
    public void setCategoryConfidence(Double categoryConfidence) { this.categoryConfidence = categoryConfidence; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    @Override
    public String toString() {
        return "TransactionAndCategoryParseResult{" +
                "amount=" + amount +
                ", type=" + type +
                ", merchant='" + merchant + '\'' +
                ", description='" + description + '\'' +
                ", transactionDate=" + transactionDate +
                ", confidence=" + confidence +
                ", categoryId=" + categoryId +
                ", categoryName='" + categoryName + '\'' +
                ", categoryConfidence=" + categoryConfidence +
                '}';
    }
}
