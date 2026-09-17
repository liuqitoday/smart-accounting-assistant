package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.util.AppClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 记账解析端点（/parse、/parse-only）每用户每日限流。
 * 内存计数（单机部署足够；重启清零可接受），按 {@link AppClock} 的
 * Asia/Shanghai 日界翻转。与 AnalysisChatService 的问数限流互相独立。
 */
@Service
public class AiRateLimitService {

    private final int dailyLimit;
    private final ConcurrentHashMap<String, DailyCounter> counters = new ConcurrentHashMap<>();
    private final AtomicReference<LocalDate> lastSweepDay = new AtomicReference<>(AppClock.today());

    public AiRateLimitService(@Value("${app.ai.daily-limit:200}") int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /**
     * 检查并消耗一次当日配额，超限抛 BusinessException（400）。
     * 在端点入口调用：解析失败也计次（实现简单；200/天的额度足够宽裕）。
     */
    public void checkAndConsume(String username) {
        LocalDate today = AppClock.today();
        sweepIfDayChanged(today);
        DailyCounter counter = counters.compute(username, (k, v) ->
                (v == null || !v.day.equals(today)) ? new DailyCounter(today) : v);
        if (counter.count.incrementAndGet() > dailyLimit) {
            throw new BusinessException("今日 AI 解析次数已达上限（" + dailyLimit + "次），明日再来");
        }
    }

    /** 跨日时清掉昨日条目，避免不活跃用户的计数残留（用户量级小，全量清扫开销可忽略）。 */
    private void sweepIfDayChanged(LocalDate today) {
        LocalDate last = lastSweepDay.get();
        if (today.equals(last) || !lastSweepDay.compareAndSet(last, today)) {
            return;
        }
        counters.entrySet().removeIf(e -> !e.getValue().day.equals(today));
    }

    private static final class DailyCounter {
        final LocalDate day;
        final AtomicInteger count = new AtomicInteger();

        DailyCounter(LocalDate day) {
            this.day = day;
        }
    }
}
