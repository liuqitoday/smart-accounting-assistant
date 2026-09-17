package com.liuqitech.accountingassistant.exception;

/**
 * 访问被拒绝异常（HTTP 403）。
 *
 * <p>用于账本权限不足（非成员，或角色低于操作所需）。由 {@code GlobalExceptionHandler}
 * 映射为 403。注意：账本作用域接口的主门禁在 {@code LedgerContextInterceptor} 中（直接写 403），
 * 本异常用于 {@code /api/ledgers/**} 等不经过该拦截器、由服务层校验的场景。</p>
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
