package com.liuqitech.accountingassistant.service.analysis;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisUsageOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisUsageSnapshot;
import com.liuqitech.accountingassistant.entity.AnalysisUsageDaily;
import com.liuqitech.accountingassistant.entity.AnalysisUsageEvent;
import com.liuqitech.accountingassistant.enums.AnalysisRequestClass;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.AnalysisUsageDailyRepository;
import com.liuqitech.accountingassistant.repository.AnalysisUsageEventRepository;
import com.liuqitech.accountingassistant.util.AppClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AnalysisUsageService {

    private static final Set<String> FAILED_STATUSES = Set.of(
        AnalysisResponseStatus.INVALID_PLAN.name(),
        AnalysisResponseStatus.AI_UNAVAILABLE.name(),
        AnalysisResponseStatus.QUERY_FAILED.name());

    private final AnalysisUsageDailyRepository dailyRepository;
    private final AnalysisUsageEventRepository eventRepository;
    private final int formalPerDay;
    private final int clarificationPerDay;
    private final int requestWindowCount;
    private final int requestWindowMinutes;
    private final int failureWindowCount;
    private final int failureWindowMinutes;

    public AnalysisUsageService(
            AnalysisUsageDailyRepository dailyRepository,
            AnalysisUsageEventRepository eventRepository,
            @Value("${analysis.usage.formal-per-day:30}") int formalPerDay,
            @Value("${analysis.usage.clarification-per-day:60}") int clarificationPerDay,
            @Value("${analysis.usage.request-window-count:5}") int requestWindowCount,
            @Value("${analysis.usage.request-window-minutes:10}") int requestWindowMinutes,
            @Value("${analysis.usage.failure-window-count:3}") int failureWindowCount,
            @Value("${analysis.usage.failure-window-minutes:10}") int failureWindowMinutes) {
        this.dailyRepository = dailyRepository;
        this.eventRepository = eventRepository;
        this.formalPerDay = formalPerDay;
        this.clarificationPerDay = clarificationPerDay;
        this.requestWindowCount = requestWindowCount;
        this.requestWindowMinutes = requestWindowMinutes;
        this.failureWindowCount = failureWindowCount;
        this.failureWindowMinutes = failureWindowMinutes;
    }

    @Transactional(readOnly = true)
    public void checkRequestWindow(String userId) {
        LocalDateTime since = AppClock.now().minusMinutes(requestWindowMinutes);
        long count = eventRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, since);
        if (count >= requestWindowCount) {
            throw new BusinessException("提问过于频繁，请稍后再试", "RATE_LIMITED");
        }
    }

    @Transactional(readOnly = true)
    public void checkFailureWindow(String userId) {
        LocalDateTime since = AppClock.now().minusMinutes(failureWindowMinutes);
        long count = eventRepository.countByUserIdAndCreatedAtGreaterThanEqualAndStatusIn(
            userId, since, FAILED_STATUSES);
        if (count >= failureWindowCount) {
            throw new BusinessException("近期分析失败次数过多，请稍后再试", "RATE_LIMITED");
        }
    }

    @Transactional(readOnly = true)
    public void checkDailyAllowance(String userId, AnalysisRequestClass requestClass) {
        LocalDate today = AppClock.today();
        AnalysisUsageDaily daily = dailyRepository.findByUserIdAndBusinessDate(userId, today).orElse(null);
        if (requestClass == AnalysisRequestClass.FORMAL) {
            long successful = daily == null ? 0 : daily.getSuccessfulCount();
            if (successful >= formalPerDay) {
                throw new BusinessException(
                    "今日正式分析次数已达上限（" + formalPerDay + "次），明日再来", "RATE_LIMITED");
            }
        } else if (requestClass == AnalysisRequestClass.CLARIFICATION) {
            long clarifications = daily == null ? 0 : daily.getClarificationCount();
            if (clarifications >= clarificationPerDay) {
                throw new BusinessException(
                    "今日澄清次数已达上限（" + clarificationPerDay + "次），明日再来", "RATE_LIMITED");
            }
        }
    }

    @Transactional
    public void record(String userId, AnalysisUsageOutcome outcome) {
        LocalDate today = AppClock.today();
        AnalysisUsageDaily daily = dailyRepository.findByUserIdAndBusinessDate(userId, today)
            .orElseGet(() -> {
                AnalysisUsageDaily created = new AnalysisUsageDaily();
                created.setUserId(userId);
                created.setBusinessDate(today);
                created.setSuccessfulCount(0);
                created.setClarificationCount(0);
                created.setFailedCount(0);
                return created;
            });
        incrementDaily(daily, outcome.status());
        dailyRepository.save(daily);

        AnalysisUsageEvent event = new AnalysisUsageEvent();
        event.setUserId(userId);
        event.setCreatedAt(AppClock.now());
        event.setStatus(outcome.status().name());
        event.setDurationMs(outcome.duration() == null ? 0L : outcome.duration().toMillis());
        event.setQueryCount(outcome.queryCount());
        event.setResultRowCount(outcome.resultRowCount());
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public AnalysisUsageSnapshot getToday(String userId) {
        LocalDate today = AppClock.today();
        return dailyRepository.findByUserIdAndBusinessDate(userId, today)
            .map(daily -> new AnalysisUsageSnapshot(
                today,
                daily.getSuccessfulCount(),
                daily.getClarificationCount(),
                daily.getFailedCount()))
            .orElse(new AnalysisUsageSnapshot(today, 0, 0, 0));
    }

    private static void incrementDaily(AnalysisUsageDaily daily, AnalysisResponseStatus status) {
        switch (status) {
            case OK, NO_DATA, PARTIAL_RESULT -> daily.setSuccessfulCount(daily.getSuccessfulCount() + 1);
            case CLARIFICATION_REQUIRED -> daily.setClarificationCount(daily.getClarificationCount() + 1);
            case INVALID_PLAN, AI_UNAVAILABLE, QUERY_FAILED -> daily.setFailedCount(daily.getFailedCount() + 1);
            default -> {
                // RATE_LIMITED 等不计入日额度
            }
        }
    }
}
