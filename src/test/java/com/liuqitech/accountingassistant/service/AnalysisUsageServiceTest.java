package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisUsageOutcome;
import com.liuqitech.accountingassistant.enums.AnalysisRequestClass;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.AnalysisUsageEventRepository;
import com.liuqitech.accountingassistant.service.analysis.AnalysisChatPersistenceService;
import com.liuqitech.accountingassistant.service.analysis.AnalysisUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalysisUsageServiceTest {

    @Autowired
    private AnalysisUsageService usageService;
    @Autowired
    private AnalysisChatPersistenceService persistence;
    @Autowired
    private AnalysisUsageEventRepository eventRepository;

    @Test
    void successfulAnalysisUsesThirtyPerDayButClarificationUsesSeparateSixtyQuota() {
        IntStream.range(0, 30).forEach(i -> usageService.record("alice",
            AnalysisUsageOutcome.successful(AnalysisResponseStatus.OK,
                Duration.ofMillis(10), 1, 1)));

        assertThatThrownBy(() -> usageService.checkDailyAllowance("alice", AnalysisRequestClass.FORMAL))
            .isInstanceOf(BusinessException.class);
        assertThatCode(() -> usageService.checkDailyAllowance("alice", AnalysisRequestClass.CLARIFICATION))
            .doesNotThrowAnyException();
    }

    @Test
    void allRequestsShareFivePerTenMinutesAndFailuresUseThreePerTenMinutes() {
        IntStream.range(0, 5).forEach(i -> usageService.record("alice",
            AnalysisUsageOutcome.clarification(Duration.ofMillis(5))));

        assertThatThrownBy(() -> usageService.checkRequestWindow("alice"))
            .isInstanceOf(BusinessException.class);

        IntStream.range(0, 3).forEach(i -> usageService.record("bob",
            AnalysisUsageOutcome.failed(AnalysisResponseStatus.AI_UNAVAILABLE, Duration.ofMillis(5))));
        assertThatThrownBy(() -> usageService.checkFailureWindow("bob"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void clearingMessagesDoesNotDeleteDailyUsageOrEvents() {
        usageService.record("alice", AnalysisUsageOutcome.success(Duration.ofMillis(10), 1, 2));

        persistence.clear(10L, "alice");

        assertThat(usageService.getToday("alice").successfulCount()).isEqualTo(1);
        assertThat(eventRepository.countByUserId("alice")).isEqualTo(1);
    }

    @Test
    void invalidPlanRecordIncrementsFailedCountNotSuccessfulCount() {
        usageService.record("alice",
            AnalysisUsageOutcome.failed(AnalysisResponseStatus.INVALID_PLAN, Duration.ofMillis(8)));

        assertThat(usageService.getToday("alice").failedCount()).isEqualTo(1);
        assertThat(usageService.getToday("alice").successfulCount()).isZero();
        assertThat(usageService.getToday("alice").clarificationCount()).isZero();
        assertThatCode(() -> usageService.checkDailyAllowance("alice", AnalysisRequestClass.FORMAL))
            .doesNotThrowAnyException();
    }

    @Test
    void getTodayForUnknownUserReturnsZeros() {
        var snapshot = usageService.getToday("nobody");

        assertThat(snapshot.successfulCount()).isZero();
        assertThat(snapshot.clarificationCount()).isZero();
        assertThat(snapshot.failedCount()).isZero();
        assertThat(snapshot.businessDate()).isNotNull();
    }
}
