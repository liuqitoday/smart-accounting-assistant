package com.liuqitech.accountingassistant.service.analysis;

import com.liuqitech.accountingassistant.dto.analysis.AnalysisComparisonRanges;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.enums.AnalysisComparisonMode;
import com.liuqitech.accountingassistant.enums.AnalysisPeriodPreset;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

@Component
public class AnalysisPeriodResolverImpl implements AnalysisPeriodResolver {

    @Override
    public AnalysisDateRange resolve(AnalysisPeriodSpec spec, LocalDate today) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(today, "today");
        return switch (spec.preset()) {
            case CURRENT_DAY -> new AnalysisDateRange(today, today);
            case CURRENT_WEEK -> {
                LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate weekEnd = weekStart.plusDays(6);
                yield new AnalysisDateRange(weekStart, weekEnd.isAfter(today) ? today : weekEnd);
            }
            case CURRENT_MONTH -> new AnalysisDateRange(today.withDayOfMonth(1), today);
            case CURRENT_QUARTER -> new AnalysisDateRange(quarterStart(today), today);
            case CURRENT_YEAR -> new AnalysisDateRange(today.withDayOfYear(1), today);
            case PREVIOUS_MONTH -> {
                LocalDate previous = today.minusMonths(1);
                yield new AnalysisDateRange(
                    previous.withDayOfMonth(1),
                    previous.with(TemporalAdjusters.lastDayOfMonth()));
            }
            case PREVIOUS_YEAR -> new AnalysisDateRange(
                LocalDate.of(today.getYear() - 1, 1, 1),
                LocalDate.of(today.getYear() - 1, 12, 31));
            case LAST_N_MONTHS -> {
                int count = spec.count() != null ? spec.count() : 1;
                LocalDate start = today.minusMonths(count - 1L).withDayOfMonth(1);
                yield new AnalysisDateRange(start, today);
            }
            case EXPLICIT_RANGE -> new AnalysisDateRange(spec.explicitStart(), spec.explicitEnd());
        };
    }

    @Override
    public AnalysisComparisonRanges resolveCompare(AnalysisPeriodSpec spec,
                                                   AnalysisComparisonMode mode,
                                                   LocalDate today) {
        Objects.requireNonNull(mode, "mode");
        AnalysisDateRange current = resolve(spec, today);
        AnalysisDateRange previous = switch (mode) {
            case SAME_PERIOD_LAST_YEAR -> shiftYears(current, -1);
            case PREVIOUS_PERIOD -> previousPeriod(spec.preset(), current);
        };
        return new AnalysisComparisonRanges(current, previous);
    }

    @Override
    public int bucketCount(AnalysisDateRange range, AnalysisTimeGrain grain) {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(grain, "grain");
        return switch (grain) {
            case DAY -> (int) ChronoUnit.DAYS.between(range.start(), range.end()) + 1;
            case WEEK -> {
                LocalDate first = range.start().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate last = range.end().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield (int) ChronoUnit.WEEKS.between(first, last) + 1;
            }
            case MONTH -> monthsBetweenInclusive(range.start().withDayOfMonth(1), range.end().withDayOfMonth(1));
            case QUARTER -> {
                LocalDate first = quarterStart(range.start());
                LocalDate last = quarterStart(range.end());
                yield (int) ChronoUnit.MONTHS.between(first, last) / 3 + 1;
            }
            case YEAR -> (int) ChronoUnit.YEARS.between(
                range.start().withDayOfYear(1), range.end().withDayOfYear(1)) + 1;
        };
    }

    private static AnalysisDateRange previousPeriod(AnalysisPeriodPreset preset, AnalysisDateRange current) {
        return switch (preset) {
            case CURRENT_DAY -> {
                LocalDate previous = current.start().minusDays(1);
                yield new AnalysisDateRange(previous, previous);
            }
            case CURRENT_WEEK -> {
                LocalDate start = current.start().minusWeeks(1);
                yield alignElapsedDays(current, start, start.plusDays(6));
            }
            case CURRENT_MONTH -> {
                LocalDate start = current.start().minusMonths(1);
                yield alignElapsedDays(current, start, start.with(TemporalAdjusters.lastDayOfMonth()));
            }
            case CURRENT_QUARTER -> {
                LocalDate start = current.start().minusMonths(3);
                yield alignElapsedDays(current, start, start.plusMonths(3).minusDays(1));
            }
            case CURRENT_YEAR -> {
                LocalDate start = current.start().minusYears(1);
                yield alignElapsedDays(current, start, start.plusYears(1).minusDays(1));
            }
            case PREVIOUS_MONTH -> {
                LocalDate start = current.start().minusMonths(1);
                yield new AnalysisDateRange(start, start.with(TemporalAdjusters.lastDayOfMonth()));
            }
            case PREVIOUS_YEAR -> shiftYears(current, -1);
            case LAST_N_MONTHS, EXPLICIT_RANGE -> equalLengthPreceding(current);
        };
    }

    private static AnalysisDateRange alignElapsedDays(AnalysisDateRange current,
                                                      LocalDate previousStart,
                                                      LocalDate previousCap) {
        long elapsed = ChronoUnit.DAYS.between(current.start(), current.end());
        LocalDate previousEnd = previousStart.plusDays(elapsed);
        if (previousEnd.isAfter(previousCap)) {
            previousEnd = previousCap;
        }
        return new AnalysisDateRange(previousStart, previousEnd);
    }

    private static AnalysisDateRange equalLengthPreceding(AnalysisDateRange current) {
        long days = ChronoUnit.DAYS.between(current.start(), current.end()) + 1;
        LocalDate previousEnd = current.start().minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(days - 1);
        return new AnalysisDateRange(previousStart, previousEnd);
    }

    private static AnalysisDateRange shiftYears(AnalysisDateRange range, long years) {
        return new AnalysisDateRange(range.start().plusYears(years), range.end().plusYears(years));
    }

    private static LocalDate quarterStart(LocalDate date) {
        int month = ((date.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(date.getYear(), month, 1);
    }

    private static int monthsBetweenInclusive(LocalDate start, LocalDate end) {
        return (int) ChronoUnit.MONTHS.between(start, end) + 1;
    }
}
