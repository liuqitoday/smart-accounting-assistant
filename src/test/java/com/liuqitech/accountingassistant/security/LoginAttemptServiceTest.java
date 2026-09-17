package com.liuqitech.accountingassistant.security;

import com.liuqitech.accountingassistant.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptServiceTest {

    private static final String USER = "demo";
    private static final String IP = "203.0.113.10";

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"));
        service = new LoginAttemptService(clock);
    }

    // ── 登录锁定 ──

    @Test
    void locksAfterMaxFailuresAndRejectsWithFriendlyMessage() {
        failTimes(LoginAttemptService.MAX_LOGIN_FAILURES - 1);
        assertDoesNotThrow(() -> service.checkLoginAllowed(USER, IP));

        service.recordLoginFailure(USER, IP);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkLoginAllowed(USER, IP));
        assertEquals("尝试次数过多，请 15 分钟后再试", exception.getMessage());
    }

    @Test
    void unlocksAfterLockDurationAndRestartsCounting() {
        failTimes(LoginAttemptService.MAX_LOGIN_FAILURES);
        assertThrows(BusinessException.class, () -> service.checkLoginAllowed(USER, IP));

        clock.advance(LoginAttemptService.LOGIN_LOCK_DURATION.plusSeconds(1));
        assertDoesNotThrow(() -> service.checkLoginAllowed(USER, IP));

        // 解锁后重新计数：一次新失败不应立即再锁
        service.recordLoginFailure(USER, IP);
        assertDoesNotThrow(() -> service.checkLoginAllowed(USER, IP));
    }

    @Test
    void successResetsFailureCount() {
        failTimes(LoginAttemptService.MAX_LOGIN_FAILURES - 1);
        service.recordLoginSuccess(USER, IP);

        service.recordLoginFailure(USER, IP);
        assertDoesNotThrow(() -> service.checkLoginAllowed(USER, IP));
    }

    @Test
    void failuresOutsideWindowDoNotAccumulate() {
        failTimes(LoginAttemptService.MAX_LOGIN_FAILURES - 1);
        clock.advance(LoginAttemptService.FAILURE_WINDOW.plusSeconds(1));

        // 窗口过期后这次失败按新记录计 1 次，不触发锁定
        service.recordLoginFailure(USER, IP);
        assertDoesNotThrow(() -> service.checkLoginAllowed(USER, IP));
    }

    @Test
    void lockIsScopedToUsernameIpPair() {
        failTimes(LoginAttemptService.MAX_LOGIN_FAILURES);
        assertThrows(BusinessException.class, () -> service.checkLoginAllowed(USER, IP));

        assertDoesNotThrow(() -> service.checkLoginAllowed(USER, "198.51.100.7"));
        assertDoesNotThrow(() -> service.checkLoginAllowed("another", IP));
    }

    // ── 注册限频 ──

    @Test
    void registrationLimitedPerIpPerHour() {
        for (int i = 0; i < LoginAttemptService.MAX_REGISTRATIONS_PER_HOUR; i++) {
            assertDoesNotThrow(() -> service.checkRegistrationAllowed(IP));
        }
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkRegistrationAllowed(IP));
        assertEquals("注册过于频繁，请稍后再试", exception.getMessage());

        // 其他 IP 不受影响
        assertDoesNotThrow(() -> service.checkRegistrationAllowed("198.51.100.7"));

        // 窗口过期后恢复
        clock.advance(LoginAttemptService.REGISTRATION_WINDOW.plusSeconds(1));
        assertDoesNotThrow(() -> service.checkRegistrationAllowed(IP));
    }

    // ── 过期清理 ──

    @Test
    void cleanupEvictsStaleEntries() {
        service.recordLoginFailure(USER, IP);
        service.checkRegistrationAllowed(IP);
        assertEquals(1, service.trackedLoginFailureEntries());
        assertEquals(1, service.trackedRegistrationEntries());

        // 超过失败窗口/注册窗口与清理间隔后，任意一次调用触发清扫
        clock.advance(Duration.ofMinutes(61));
        service.checkLoginAllowed("someone", "192.0.2.1");

        assertEquals(0, service.trackedLoginFailureEntries());
        assertEquals(0, service.trackedRegistrationEntries());
    }

    // ── 客户端 IP 解析 ──

    @Test
    void trustsForwardedForOnlyFromPrivateOrLoopbackProxy() {
        MockHttpServletRequest viaProxy = new MockHttpServletRequest();
        viaProxy.setRemoteAddr("127.0.0.1");
        viaProxy.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        assertEquals("203.0.113.10", service.clientIp(viaProxy));

        // 公网直连伪造 X-Forwarded-For 不可信，用 remoteAddr
        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("198.51.100.7");
        direct.addHeader("X-Forwarded-For", "1.2.3.4");
        assertEquals("198.51.100.7", service.clientIp(direct));

        MockHttpServletRequest noHeader = new MockHttpServletRequest();
        noHeader.setRemoteAddr("198.51.100.7");
        assertEquals("198.51.100.7", service.clientIp(noHeader));
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            service.recordLoginFailure(USER, IP);
        }
    }

    /** 可推进的测试时钟。 */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
