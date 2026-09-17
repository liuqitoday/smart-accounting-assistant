package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.enums.RecurringFrequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecurringBillScheduleCalculatorTest {

    @Test
    void monthlyRuleFallsBackToMonthEndWhenDayDoesNotExist() {
        LocalDate first = RecurringBillScheduleCalculator.firstRunOnOrAfter(
                RecurringFrequency.MONTHLY, LocalDate.of(2026, 2, 1), 31);
        LocalDate next = RecurringBillScheduleCalculator.nextRunAfter(
                RecurringFrequency.MONTHLY, first, 31);

        assertEquals(LocalDate.of(2026, 2, 28), first);
        assertEquals(LocalDate.of(2026, 3, 31), next);
    }

    @Test
    void firstRunMovesToNextMonthWhenCurrentMonthOccurrenceHasPassed() {
        LocalDate first = RecurringBillScheduleCalculator.firstRunOnOrAfter(
                RecurringFrequency.MONTHLY, LocalDate.of(2026, 7, 6), 5);

        assertEquals(LocalDate.of(2026, 8, 5), first);
    }
}
