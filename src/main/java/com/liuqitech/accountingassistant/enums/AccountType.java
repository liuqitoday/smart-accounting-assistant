package com.liuqitech.accountingassistant.enums;

/**
 * 账户类型（仅资产类）。
 *
 * <p>主要用于前端展示分组与默认图标；余额计算对所有类型一致：期初 + 收入 − 支出。
 * 暂不含信用卡/花呗等负债类（余额为负表示欠款）——预留为后续扩展。</p>
 */
public enum AccountType {
    /** 现金 */
    CASH,
    /** 银行卡（储蓄卡） */
    DEBIT_CARD,
    /** 网络支付（支付宝 / 微信 / 云闪付） */
    VIRTUAL,
    /** 投资理财（基金 / 股票 / 余额宝） */
    INVESTMENT,
    /** 储值卡（公交卡 / 会员卡 / 礼品卡） */
    PREPAID,
    /** 其他 */
    OTHER
}
