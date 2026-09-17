package com.liuqitech.accountingassistant.enums;

/**
 * 账本成员角色。
 *
 * <p>枚举顺序即权限高低：OWNER(0) &gt; EDITOR(1) &gt; VIEWER(2)。
 * {@link #atLeast(LedgerRole)} 基于 ordinal 比较，<b>切勿调整声明顺序</b>，否则权限判断会反转。</p>
 *
 * <ul>
 *     <li>OWNER  —— 所有者：成员管理、改名/删除账本、转让所有权。</li>
 *     <li>EDITOR —— 可编辑：交易/标签增删改、导入。</li>
 *     <li>VIEWER —— 仅查看：列表、统计、导出。</li>
 * </ul>
 */
public enum LedgerRole {
    OWNER,
    EDITOR,
    VIEWER;

    /**
     * 当前角色权限是否不低于 {@code min}。
     * 例如 {@code EDITOR.atLeast(EDITOR)} 与 {@code OWNER.atLeast(EDITOR)} 为 true，
     * {@code VIEWER.atLeast(EDITOR)} 为 false。
     */
    public boolean atLeast(LedgerRole min) {
        return this.ordinal() <= min.ordinal();
    }
}
