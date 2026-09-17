package com.liuqitech.accountingassistant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 会话认证纪元检查：为每个已认证会话打认证纪元戳（AUTH_EPOCH），
 * 纪元早于 {@link SessionRevocationRegistry} 吊销纪元的会话就地失效并清空认证，
 * 请求随后被授权层按未认证拒绝（401，统一 ApiResponse 包体）。
 *
 * <p>纪元戳在会话首个经过本过滤器的请求上惰性补打，天然覆盖 remember-me
 * 自动续登创建的会话。非 Spring bean：由 SecurityConfig 内联构造，
 * 避免 Boot 把 Filter 类型的 bean 重复注册进 servlet 过滤器链。</p>
 */
public class SessionEpochFilter extends OncePerRequestFilter {

    public static final String AUTH_EPOCH = "AUTH_EPOCH";

    private final SessionRevocationRegistry revocationRegistry;

    public SessionEpochFilter(SessionRevocationRegistry revocationRegistry) {
        this.revocationRegistry = revocationRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (session != null && authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            Long epoch = (Long) session.getAttribute(AUTH_EPOCH);
            if (epoch == null) {
                session.setAttribute(AUTH_EPOCH, revocationRegistry.nextEpoch());
            } else if (epoch < revocationRegistry.revocationEpoch(authentication.getName())) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
