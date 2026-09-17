package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.enums.RecurringFrequency;
import com.liuqitech.accountingassistant.exception.BusinessException;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 周期日期计算保持为纯函数，便于覆盖月底等边界。
 */
public final class RecurringBillScheduleCalculator {

    private RecurringBillScheduleCalculator() {
    }

    public static LocalDate firstRunOnOrAfter(RecurringFrequency frequency, LocalDate startDate, int dayOfMonth) {
        validate(frequency, startDate, dayOfMonth);
        if (frequency != RecurringFrequency.MONTHLY) {
            throw new BusinessException("暂不支持该周期: " + frequency);
        }

        YearMonth startMonth = YearMonth.from(startDate);
        LocalDate candidate = atDay(startMonth, dayOfMonth);
        if (candidate.isBefore(startDate)) {
            return atDay(startMonth.plusMonths(1), dayOfMonth);
        }
        return candidate;
    }

    public static LocalDate nextRunAfter(RecurringFrequency frequency, LocalDate currentRunDate, int dayOfMonth) {
        validate(frequency, currentRunDate, dayOfMonth);
        if (frequency != RecurringFrequency.MONTHLY) {
            throw new BusinessException("暂不支持该周期: " + frequency);
        }
        return atDay(YearMonth.from(currentRunDate).plusMonths(1), dayOfMonth);
    }

    private static LocalDate atDay(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private static void validate(RecurringFrequency frequency, LocalDate date, int dayOfMonth) {
        if (frequency == null) {
            throw new BusinessException("周期不能为空");
        }
        if (date == null) {
            throw new BusinessException("日期不能为空");
        }
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new BusinessException("生成日必须在 1-31 之间");
        }
    }
}
