package com.liuqitech.accountingassistant.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 切换当前账本请求
 */
public class SetActiveLedgerRequest {

    @NotNull(message = "账本ID不能为空")
    private Long ledgerId;

    public Long getLedgerId() {
        return ledgerId;
    }

    public void setLedgerId(Long ledgerId) {
        this.ledgerId = ledgerId;
    }
}
