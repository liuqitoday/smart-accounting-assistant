package com.liuqitech.accountingassistant.dto;

import com.liuqitech.accountingassistant.enums.RecurringFrequency;
import com.liuqitech.accountingassistant.enums.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CreateRecurringBillRequest {

    @NotBlank(message = "规则名称不能为空")
    private String name;

    private Boolean enabled = true;

    @NotNull(message = "金额不能为空")
    @Positive(message = "金额必须大于 0")
    private BigDecimal amount;

    @NotNull(message = "交易类型不能为空")
    private TransactionType type;

    @NotBlank(message = "描述不能为空")
    private String description;

    private Long categoryId;

    private Long accountId;
    private Long counterAccountId;
    private String parsedMerchant;
    private String relatedUser;
    private String note;

    @NotNull(message = "周期不能为空")
    private RecurringFrequency frequency = RecurringFrequency.MONTHLY;

    @NotNull(message = "生成日不能为空")
    @Min(value = 1, message = "生成日不能小于 1")
    @Max(value = 31, message = "生成日不能大于 31")
    private Integer dayOfMonth;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    private LocalDate endDate;
    private List<Long> tagIds;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
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

    public String getParsedMerchant() {
        return parsedMerchant;
    }

    public void setParsedMerchant(String parsedMerchant) {
        this.parsedMerchant = parsedMerchant;
    }

    public String getRelatedUser() {
        return relatedUser;
    }

    public void setRelatedUser(String relatedUser) {
        this.relatedUser = relatedUser;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public RecurringFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(RecurringFrequency frequency) {
        this.frequency = frequency;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }
}
