package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request for editing a transaction.
 */
public class UpdateTransactionRequest {

    @NotNull(message = "金额不能为空")
    @Positive(message = "金额必须大于 0")
    private BigDecimal amount;

    @NotNull(message = "交易类型不能为空")
    private TransactionType type;

    @NotBlank(message = "描述不能为空")
    private String description;

    @NotNull(message = "交易日期不能为空")
    private LocalDate transactionDate;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    private String parsedMerchant;
    private String note;
    private String relatedUser;
    private Long accountId;
    /** Null/omitted preserves tags; an empty list explicitly clears them. */
    @Size(max = 100, message = "标签数量不能超过 100")
    private List<@NotNull @Positive Long> tagIds;

    public UpdateTransactionRequest() {}

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

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getParsedMerchant() {
        return parsedMerchant;
    }

    public void setParsedMerchant(String parsedMerchant) {
        this.parsedMerchant = parsedMerchant;
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

    public List<Long> getTagIds() { return tagIds; }

    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }
}
