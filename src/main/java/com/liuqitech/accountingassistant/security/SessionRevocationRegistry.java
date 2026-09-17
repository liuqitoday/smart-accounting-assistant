package com.liuqitech.accountingassistant.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户会话吊销纪元（JVM 内存态逻辑时钟，单实例假设）。
 *
 * <p>改密码/登出时对用户记录一个吊销纪元；{@link SessionEpochFilter} 在后续请求中
 * 将会话上的认证纪元戳与之比较，早于吊销纪元的会话被就地失效。用逻辑时钟而非
 * 墙钟避免同毫秒内"登录-吊销"顺序不可分辨的问题。内存态与内嵌 Tomcat 的
 * 内存会话生命周期一致：进程重启两者同时清空，语义自洽。</p>
 */
@Component
public class SessionRevocationRegistry {

    private final AtomicLong clock = new AtomicLong();
    private final Map<String, Long> revocationEpochByUser = new ConcurrentHashMap<>();

    /** 取下一个逻辑时钟值，用于给会话打认证纪元戳。 */
    public long nextEpoch() {
        return clock.incrementAndGet();
    }

    /** 记录该用户的吊销纪元，并返回一个晚于它的新纪元（供当前设备重新打戳保活）。 */
    public long revoke(String username) {
        revocationEpochByUser.put(username, clock.incrementAndGet());
        return clock.incrementAndGet();
    }

    /** 该用户的吊销纪元；从未吊销过返回 0。 */
    public long revocationEpoch(String username) {
        return revocationEpochByUser.getOrDefault(username, 0L);
    }
}
