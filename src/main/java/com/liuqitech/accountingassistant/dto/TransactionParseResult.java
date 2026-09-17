package com.liuqitech.accountingassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liuqitech.accountingassistant.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI解析结果DTO
 */
public class TransactionParseResult {

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

    @JsonProperty("confidence")
    private BigDecimal confidence;
    
    // 默认构造函数
    public TransactionParseResult() {}
    
    // 构造函数
    public TransactionParseResult(BigDecimal amount, TransactionType type, String merchant,
                                String description, LocalDate transactionDate, String note,
                                String relatedUser, BigDecimal confidence) {
        this.amount = amount;
        this.type = type;
        this.merchant = merchant;
        this.description = description;
        this.transactionDate = transactionDate;
        this.note = note;
        this.relatedUser = relatedUser;
        this.confidence = confidence;
    }
    
    // Getters and Setters
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
    
    public String getMerchant() {
        return merchant;
    }
    
    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
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

    public BigDecimal getConfidence() {
        return confidence;
    }
    
    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }
    
    @Override
    public String toString() {
        return "TransactionParseResult{" +
                "amount=" + amount +
                ", type=" + type +
                ", merchant='" + merchant + '\'' +
                ", description='" + description + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
