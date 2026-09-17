package com.liuqitech.accountingassistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建账户间转账的请求体。
 * 转账以 type=TRANSFER 的交易行存储：fromAccountId 为转出方，toAccountId 为转入方。
 */
public class CreateTransferRequest {

    /** 转出账户 */
    private Long fromAccountId;

    /** 转入账户 */
    private Long toAccountId;

    /** 转账金额（> 0） */
    private BigDecimal amount;

    /** 转账日期，为空则取今天 */
    private LocalDate transferDate;

    /** 备注（可选） */
    private String note;

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public void setFromAccountId(Long fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public void setToAccountId(Long toAccountId) {
        this.toAccountId = toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public void setTransferDate(LocalDate transferDate) {
        this.transferDate = transferDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
