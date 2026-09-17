package com.liuqitech.accountingassistant.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InMemoryTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GracePeriodRememberMeServices 的纯单元测试：不依赖 Spring 上下文，
 * 直接驱动 protected processAutoLoginCookie 验证轮换/宽限/被盗三条路径。
 */
class GracePeriodRememberMeServicesTest {

    private static final String KEY = "test-key";
    private static final String USERNAME = "alice";
    private static final String SERIES = "series-1";

    private InMemoryTokenRepositoryImpl tokenRepository;
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        tokenRepository = new InMemoryTokenRepositoryImpl();
        tokenRepository.createNewToken(new PersistentRememberMeToken(USERNAME, SERIES, "token-1", new Date()));
        userDetailsService = username -> User.withUsername(username).password("pw").roles("USER").build();
    }

    private GracePeriodRememberMeServices services(long graceMillis) {
        return new GracePeriodRememberMeServices(KEY, userDetailsService, tokenRepository, graceMillis);
    }

    private UserDetails autoLogin(GracePeriodRememberMeServices services, String token,
                                  MockHttpServletResponse response) {
        return services.processAutoLoginCookie(new String[]{SERIES, token},
                new MockHttpServletRequest(), response);
    }

    @Test
    void normalAutoLoginRotatesToken() {
        GracePeriodRememberMeServices services = services(60_000);
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserDetails user = autoLogin(services, "token-1", response);

        assertEquals(USERNAME, user.getUsername());
        assertNotEquals("token-1", tokenRepository.getTokenForSeries(SERIES).getTokenValue());
        assertNotNull(response.getCookie("remember-me"));
    }

    @Test
    void graceReplayWithOldTokenIsAcceptedWithoutSecondRotation() {
        GracePeriodRememberMeServices services = services(60_000);
        autoLogin(services, "token-1", new MockHttpServletResponse());
        String rotated = tokenRepository.getTokenForSeries(SERIES).getTokenValue();

        MockHttpServletResponse replayResponse = new MockHttpServletResponse();
        UserDetails user = autoLogin(services, "token-1", replayResponse);

        assertEquals(USERNAME, user.getUsername());
        // 未二次轮换：仓库里的 token 仍是第一次轮换的结果
        assertEquals(rotated, tokenRepository.getTokenForSeries(SERIES).getTokenValue());
        // 重发的 cookie 携带已轮换的新 token
        assertNotNull(replayResponse.getCookie("remember-me"));
    }

    @Test
    void replayOutsideGraceWindowRevokesAllTokens() {
        GracePeriodRememberMeServices services = services(0); // 宽限为 0 → 立即过期
        autoLogin(services, "token-1", new MockHttpServletResponse());

        assertThrows(RememberMeAuthenticationException.class,
                () -> autoLogin(services, "token-1", new MockHttpServletResponse()));
        assertNull(tokenRepository.getTokenForSeries(SERIES)); // 全部作废
    }

    @Test
    void forgedTokenRevokesAllTokens() {
        GracePeriodRememberMeServices services = services(60_000);

        assertThrows(RememberMeAuthenticationException.class,
                () -> autoLogin(services, "forged-token", new MockHttpServletResponse()));
        assertNull(tokenRepository.getTokenForSeries(SERIES));
    }

    @Test
    void expiredTokenIsRejectedButNotRevoked() {
        Date eightDaysAgo = new Date(System.currentTimeMillis() - 8L * 24 * 3600 * 1000);
        tokenRepository = new InMemoryTokenRepositoryImpl();
        tokenRepository.createNewToken(new PersistentRememberMeToken(USERNAME, SERIES, "token-1", eightDaysAgo));
        GracePeriodRememberMeServices services = services(60_000);
        services.setTokenValiditySeconds(7 * 24 * 3600);

        assertThrows(RememberMeAuthenticationException.class,
                () -> autoLogin(services, "token-1", new MockHttpServletResponse()));
        // 过期是正常淘汰而非被盗，令牌行保留（由使用时校验挡住）
        assertNotNull(tokenRepository.getTokenForSeries(SERIES));
    }
}
