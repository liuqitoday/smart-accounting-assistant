package com.liuqitech.accountingassistant.enums;

/**
 * 交易类型枚举
 */
public enum TransactionType {
    /**
     * 收入
     */
    INCOME,
    
    /**
     * 支出
     */
    EXPENSE,

    /**
     * 账户间转账：account_id 为转出方，counter_account_id 为转入方；
     * 不计入收入/支出统计，仅影响账户余额。
     */
    TRANSFER
}
