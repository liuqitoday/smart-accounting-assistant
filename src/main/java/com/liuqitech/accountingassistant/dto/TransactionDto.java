package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Public transaction representation returned by API endpoints.
 *
 * <p>Internal ownership and lifecycle fields such as ledgerId and deletedAt stay
 * inside the persistence layer.</p>
 */
public class TransactionDto {

    private Long id;
    private BigDecimal amount;
    private TransactionType type;
    private String description;
    private String originalText;
    private Long categoryId;
    private String category;
    private String categoryName;
    private Long parentCategoryId;
    private String parentCategoryName;
    private LocalDate transactionDate;
    private String note;
    private String relatedUser;
    private String parsedMerchant;
    private BigDecimal parsedAmount;
    private BigDecimal confidenceScore;
    private String aiModelUsed;
    private String createdBy;
    private Long accountId;
    private Long counterAccountId;
    private Long recurringBillId;
    private LocalDate recurringOccurrenceDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TagDto> tags;

    public TransactionDto() {}

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public String getParsedMerchant() {
        return parsedMerchant;
    }

    public void setParsedMerchant(String parsedMerchant) {
        this.parsedMerchant = parsedMerchant;
    }

    public BigDecimal getParsedAmount() {
        return parsedAmount;
    }

    public void setParsedAmount(BigDecimal parsedAmount) {
        this.parsedAmount = parsedAmount;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getCounterAccountId() {
        return counterAccountId;
    }

    public void setCounterAccountId(Long counterAccountId) {
        this.counterAccountId = counterAccountId;
    }

    public Long getRecurringBillId() {
        return recurringBillId;
    }

    public void setRecurringBillId(Long recurringBillId) {
        this.recurringBillId = recurringBillId;
    }

    public LocalDate getRecurringOccurrenceDate() {
        return recurringOccurrenceDate;
    }

    public void setRecurringOccurrenceDate(LocalDate recurringOccurrenceDate) {
        this.recurringOccurrenceDate = recurringOccurrenceDate;
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

    public List<TagDto> getTags() {
        return tags;
    }

    public void setTags(List<TagDto> tags) {
        this.tags = tags;
    }
}
