package com.liuqitech.accountingassistant.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在标准持久化令牌 Remember-Me 之上增加并发轮换防护。
 *
 * <p>PWA/标签页休眠数天后恢复时，页面会用同一旧令牌并发发出多个请求：
 * 第一个请求轮换 token 后，其余请求按原逻辑会被误判为 cookie 被盗，
 * 导致该用户全部令牌被作废、强制登出。本类将自动续登串行化，并在轮换后的
 * 宽限窗口内把携带旧 token 的请求视为并发重放，直接下发已轮换的新 token 放行。</p>
 *
 * <p>宽限窗口外的 token 不匹配仍按被盗处理（作废该用户全部令牌），但把
 * {@link CookieTheftException} 转译为 {@link RememberMeAuthenticationException}：
 * 前者会从 RememberMeAuthenticationFilter 直接抛穿变成 500，后者由
 * autoLogin 捕获后清 cookie、按未认证放行，最终表现为 401，交由前端强制登出。</p>
 *
 * <p>宽限缓存为 JVM 内存态，仅适用于单实例部署。</p>
 */
public class GracePeriodRememberMeServices extends PersistentTokenBasedRememberMeServices {

    private record RecentRotation(String oldToken, long expiresAtMillis) {}

    private final PersistentTokenRepository tokenRepository;
    private final long graceMillis;
    private final Map<String, RecentRotation> recentRotations = new ConcurrentHashMap<>();

    public GracePeriodRememberMeServices(String key, UserDetailsService userDetailsService,
                                         PersistentTokenRepository tokenRepository, long graceMillis) {
        super(key, userDetailsService, tokenRepository);
        this.tokenRepository = tokenRepository;
        this.graceMillis = graceMillis;
    }

    @Override
    protected synchronized UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
                                                              HttpServletResponse response) {
        if (cookieTokens.length != 2) {
            throw new InvalidCookieException(
                    "Cookie token did not contain 2 tokens, but contained '" + Arrays.asList(cookieTokens) + "'");
        }
        String presentedSeries = cookieTokens[0];
        String presentedToken = cookieTokens[1];
        long now = System.currentTimeMillis();
        recentRotations.values().removeIf(rotation -> rotation.expiresAtMillis() <= now);

        RecentRotation recent = recentRotations.get(presentedSeries);
        if (recent != null && recent.oldToken().equals(presentedToken)) {
            // 宽限期内的并发重放：重发已轮换的新 token，不再次轮换、不作废令牌
            PersistentRememberMeToken current = tokenRepository.getTokenForSeries(presentedSeries);
            if (current == null) {
                throw new RememberMeAuthenticationException("宽限重放时 series 对应的持久化令牌已不存在");
            }
            setCookie(new String[]{presentedSeries, current.getTokenValue()}, getTokenValiditySeconds(),
                    request, response);
            return getUserDetailsService().loadUserByUsername(current.getUsername());
        }

        UserDetails userDetails;
        try {
            userDetails = super.processAutoLoginCookie(cookieTokens, request, response);
        } catch (CookieTheftException ex) {
            logger.warn("Remember-Me token 不匹配，已作废该用户全部令牌（可能被盗）: " + ex.getMessage());
            throw new RememberMeAuthenticationException("Remember-Me token 不匹配，已作废全部令牌", ex);
        }
        PersistentRememberMeToken rotated = tokenRepository.getTokenForSeries(presentedSeries);
        if (rotated != null) {
            recentRotations.put(presentedSeries,
                    new RecentRotation(presentedToken, now + graceMillis));
        }
        return userDetails;
    }
}
