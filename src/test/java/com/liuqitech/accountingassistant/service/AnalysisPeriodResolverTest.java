package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisComparisonRanges;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.enums.AnalysisComparisonMode;
import com.liuqitech.accountingassistant.enums.AnalysisPeriodPreset;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPeriodResolver;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPeriodResolverImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisPeriodResolverTest {

    private AnalysisPeriodResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AnalysisPeriodResolverImpl();
    }

    @Test
    void currentMonthBeforeMonthEndAlignsPreviousMonthToElapsedDays() {
        AnalysisComparisonRanges ranges = resolver.resolveCompare(
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
            AnalysisComparisonMode.PREVIOUS_PERIOD, LocalDate.of(2026, 8, 25));

        assertThat(ranges.current()).isEqualTo(new AnalysisDateRange(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)));
        assertThat(ranges.previous()).isEqualTo(new AnalysisDateRange(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 25)));
    }

    @Test
    void explicitPresetsResolveToClosedAsiaShanghaiDates() {
        assertThat(resolver.resolve(AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_YEAR),
            LocalDate.of(2026, 8, 25)))
            .isEqualTo(new AnalysisDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 25)));
        assertThat(resolver.resolve(AnalysisPeriodSpec.of(AnalysisPeriodPreset.PREVIOUS_YEAR),
            LocalDate.of(2026, 8, 25)))
            .isEqualTo(new AnalysisDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
    }

    @Test
    void samePeriodLastYearHandlesLeapDayAndExplicitRange() {
        AnalysisComparisonRanges ranges = resolver.resolveCompare(
            AnalysisPeriodSpec.explicit(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)),
            AnalysisComparisonMode.SAME_PERIOD_LAST_YEAR, LocalDate.of(2024, 2, 29));

        assertThat(ranges.current()).isEqualTo(
            new AnalysisDateRange(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)));
        assertThat(ranges.previous()).isEqualTo(
            new AnalysisDateRange(LocalDate.of(2023, 2, 1), LocalDate.of(2023, 2, 28)));
    }

    @ParameterizedTest
    @CsvSource({"DAY,31", "WEEK,6", "MONTH,1", "QUARTER,1", "YEAR,1"})
    void bucketCountUsesClosedRangesForEveryGrain(AnalysisTimeGrain grain, int expected) {
        assertThat(resolver.bucketCount(
            new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)), grain))
            .isEqualTo(expected);
    }

    @Test
    void currentDayResolvesToTodayOnly() {
        assertThat(resolver.resolve(AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_DAY),
            LocalDate.of(2026, 8, 25)))
            .isEqualTo(new AnalysisDateRange(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 25)));
    }

    @Test
    void currentWeekStartsMondayAndEndsToday() {
        // 2026-08-25 是周二；当周周一为 2026-08-24
        assertThat(resolver.resolve(AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_WEEK),
            LocalDate.of(2026, 8, 25)))
            .isEqualTo(new AnalysisDateRange(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 25)));
    }

    @Test
    void currentQuarterStartsAtQuarterBeginThroughToday() {
        assertThat(resolver.resolve(AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_QUARTER),
            LocalDate.of(2026, 8, 25)))
            .isEqualTo(new AnalysisDateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 25)));
    }

    @Test
    void previousMonthResolvesToFullPreviousCalendarMonth() {
        assertThat(resolver.resolve(AnalysisPeriodSpec.of(AnalysisPeriodPreset.PREVIOUS_MONTH),
            LocalDate.of(2026, 8, 25)))
            .isEqualTo(new AnalysisDateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
    }

    @Test
    void lastNMonthsCountsBackFromTodayAsClosedRange() {
        assertThat(resolver.resolve(AnalysisPeriodSpec.lastNMonths(3),
            LocalDate.of(2026, 8, 25)))
            .isEqualTo(new AnalysisDateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 25)));
    }

    @Test
    void explicitRangePreviousPeriodIsEqualLengthImmediatelyPreceding() {
        AnalysisComparisonRanges ranges = resolver.resolveCompare(
            AnalysisPeriodSpec.explicit(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)),
            AnalysisComparisonMode.PREVIOUS_PERIOD, LocalDate.of(2024, 2, 29));

        assertThat(ranges.current()).isEqualTo(
            new AnalysisDateRange(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)));
        assertThat(ranges.previous()).isEqualTo(
            new AnalysisDateRange(LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 31)));
    }
}
