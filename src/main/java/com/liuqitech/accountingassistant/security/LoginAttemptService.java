package com.liuqitech.accountingassistant.security;

import com.liuqitech.accountingassistant.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 登录防爆破 + 注册限频（内存实现，单机部署足够；重启即清零可接受）。
 *
 * <p>登录按「用户名+IP」计数：15 分钟窗口内失败 {@value #MAX_LOGIN_FAILURES} 次
 * 锁定 15 分钟，成功登录清零。注册按 IP 固定窗口限频：每 IP 每小时最多
 * {@value #MAX_REGISTRATIONS_PER_HOUR} 次。两类记录均惰性过期 + 周期清扫，避免 Map 无限增长。</p>
 *
 * <p>取 IP：仅当直连方是本机/内网地址（即请求来自本地反向代理）才信任
 * X-Forwarded-For 首段；公网直连时该头可被客户端伪造，一律用 remoteAddr。</p>
 */
@Service
public class LoginAttemptService {

    static final int MAX_LOGIN_FAILURES = 5;
    static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final int MAX_REGISTRATIONS_PER_HOUR = 5;
    static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(10);

    private final Clock clock;
    private final ConcurrentHashMap<String, FailureRecord> loginFailures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RegistrationWindow> registrations = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastCleanup;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
        this.lastCleanup = new AtomicReference<>(clock.instant());
    }

    // ── 登录防爆破 ──

    /** 登录前调用：锁定中直接拒绝（400，不触发认证、不泄露锁定针对的是否为真实用户）。 */
    public void checkLoginAllowed(String username, String ip) {
        Instant now = clock.instant();
        maybeCleanup(now);
        FailureRecord record = loginFailures.get(key(username, ip));
        if (record != null && record.isLocked(now)) {
            throw new BusinessException("尝试次数过多，请 15 分钟后再试");
        }
    }

    /** 认证失败后调用：窗口内累计，达到上限即锁定。 */
    public void recordLoginFailure(String username, String ip) {
        Instant now = clock.instant();
        maybeCleanup(now);
        loginFailures.compute(key(username, ip), (k, record) -> {
            if (record == null || record.isExpired(now)) {
                record = new FailureRecord();
            }
            record.count++;
            record.lastFailure = now;
            if (record.count >= MAX_LOGIN_FAILURES) {
                record.lockedUntil = now.plus(LOGIN_LOCK_DURATION);
            }
            return record;
        });
    }

    /** 登录成功后调用：清零该「用户名+IP」的失败记录。 */
    public void recordLoginSuccess(String username, String ip) {
        loginFailures.remove(key(username, ip));
    }

    // ── 注册限频 ──

    /** 注册入口调用：检查并计入一次尝试（含失败尝试，防用户名枚举式滥用）。 */
    public void checkRegistrationAllowed(String ip) {
        Instant now = clock.instant();
        maybeCleanup(now);
        RegistrationWindow window = registrations.compute(ip, (k, w) -> {
            if (w == null || w.isExpired(now)) {
                w = new RegistrationWindow(now);
            }
            w.count++;
            return w;
        });
        if (window.count > MAX_REGISTRATIONS_PER_HOUR) {
            throw new BusinessException("注册过于频繁，请稍后再试");
        }
    }

    // ── 客户端 IP 解析 ──

    /**
     * forward-headers-strategy=framework 时 ForwardedHeaderFilter 已把 X-Forwarded-For
     * 首段写入 getRemoteAddr() 并隐藏该头，此处直接得到客户端 IP；
     * 未经过该过滤器（如测试、strategy=none）时仅当直连方是本机/内网反代才信任 XFF——
     * 公网直连伪造的 XFF 不可信，一律用 remoteAddr。
     */
    public String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && isPrivateOrLoopback(remoteAddr)) {
            return forwarded.split(",")[0].trim();
        }
        return remoteAddr;
    }

    private static boolean isPrivateOrLoopback(String addr) {
        if (addr == null || addr.isBlank()) {
            return false;
        }
        try {
            // 参数是 IP 字面量时不触发 DNS 解析
            InetAddress inet = InetAddress.getByName(addr);
            return inet.isLoopbackAddress() || inet.isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    // ── 内部结构与清理 ──

    private static String key(String username, String ip) {
        return username + "|" + ip;
    }

    /** 周期性清扫过期条目，防止内存无限增长（CAS 保证并发下最多一个线程清扫）。 */
    private void maybeCleanup(Instant now) {
        Instant last = lastCleanup.get();
        if (Duration.between(last, now).compareTo(CLEANUP_INTERVAL) < 0) {
            return;
        }
        if (!lastCleanup.compareAndSet(last, now)) {
            return;
        }
        loginFailures.entrySet().removeIf(e -> e.getValue().isExpired(now));
        registrations.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    /** 仅测试用：当前登录失败追踪条目数。 */
    int trackedLoginFailureEntries() {
        return loginFailures.size();
    }

    /** 仅测试用：当前注册窗口追踪条目数。 */
    int trackedRegistrationEntries() {
        return registrations.size();
    }

    private static final class FailureRecord {
        int count;
        Instant lastFailure;
        Instant lockedUntil;

        boolean isLocked(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        /** 锁已过期，或未锁定但最后一次失败超出计数窗口 → 记录作废，重新计数。 */
        boolean isExpired(Instant now) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            return lastFailure == null || lastFailure.plus(FAILURE_WINDOW).isBefore(now);
        }
    }

    private static final class RegistrationWindow {
        final Instant windowStart;
        int count;

        RegistrationWindow(Instant windowStart) {
            this.windowStart = windowStart;
        }

        boolean isExpired(Instant now) {
            return windowStart.plus(REGISTRATION_WINDOW).isBefore(now);
        }
    }
}
