package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request for saving a parsed or manually entered transaction.
 */
public class SaveTransactionRequest {

    @NotNull(message = "金额不能为空")
    @Positive(message = "金额必须大于 0")
    private BigDecimal amount;

    @NotNull(message = "交易类型不能为空")
    private TransactionType type;

    @NotBlank(message = "描述不能为空")
    private String description;

    @NotBlank(message = "原始文本不能为空")
    private String originalText;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotNull(message = "交易日期不能为空")
    private LocalDate transactionDate;

    private String parsedMerchant;
    private BigDecimal confidenceScore;
    private String aiModelUsed;
    private String note;
    private String relatedUser;
    private Long aiSuggestedCategoryId;
    private Long accountId;

    public SaveTransactionRequest() {}

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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
}
