package com.liuqitech.accountingassistant.interceptor;

import com.liuqitech.accountingassistant.enums.LedgerRole;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求属性读取登录用户名与当前账本上下文的小工具。
 *
 * <p>{@code username} 由 {@link AuthenticatedUserInterceptor} 写入；{@code ledgerId} 与
 * {@code ledgerRole} 由 {@link LedgerContextInterceptor} 写入。控制器据此向服务层传递账本作用域。</p>
 */
public final class LedgerContext {

    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_LEDGER_ID = "ledgerId";
    public static final String ATTR_ROLE = "ledgerRole";

    private LedgerContext() {}

    public static String username(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_USERNAME);
    }

    public static Long ledgerId(HttpServletRequest request) {
        return (Long) request.getAttribute(ATTR_LEDGER_ID);
    }

    public static LedgerRole role(HttpServletRequest request) {
        return (LedgerRole) request.getAttribute(ATTR_ROLE);
    }
}
