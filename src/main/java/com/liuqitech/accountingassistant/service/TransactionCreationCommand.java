package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 服务内部使用的交易创建命令，避免周期账单复用交易写入逻辑时调用 HTTP DTO。
 */
public class TransactionCreationCommand {

    private Long ledgerId;
    private String username;
    private BigDecimal amount;
    private TransactionType type;
    private String description;
    private String originalText;
    private Long categoryId;
    private LocalDate transactionDate;
    private String parsedMerchant;
    private BigDecimal confidenceScore;
    private String aiModelUsed;
    private String note;
    private String relatedUser;
    private Long accountId;
    private Long recurringBillId;
    private LocalDate recurringOccurrenceDate;
    private Set<Tag> tags = new HashSet<>();

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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
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

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags == null ? new HashSet<>() : new HashSet<>(tags);
    }
}
