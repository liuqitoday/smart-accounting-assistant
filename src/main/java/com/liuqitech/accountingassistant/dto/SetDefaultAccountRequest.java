package com.liuqitech.accountingassistant.dto;

/**
 * 设置当前用户在当前账本的默认账户。
 *
 * <p>{@link #accountId} 传 {@code null} 表示清除该偏好（默认账户是可选设置）。</p>
 */
public class SetDefaultAccountRequest {

    private Long accountId;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
}
