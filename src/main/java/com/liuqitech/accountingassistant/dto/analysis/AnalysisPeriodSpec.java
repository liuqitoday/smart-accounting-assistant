package com.liuqitech.accountingassistant.dto.analysis;

import com.liuqitech.accountingassistant.enums.AnalysisPeriodPreset;

import java.time.LocalDate;
import java.util.Objects;

public record AnalysisPeriodSpec(
        AnalysisPeriodPreset preset,
        LocalDate explicitStart,
        LocalDate explicitEnd,
        Integer count) {

    public AnalysisPeriodSpec {
        Objects.requireNonNull(preset, "preset");
    }

    public static AnalysisPeriodSpec of(AnalysisPeriodPreset preset) {
        return new AnalysisPeriodSpec(Objects.requireNonNull(preset), null, null, null);
    }

    public static AnalysisPeriodSpec explicit(LocalDate start, LocalDate end) {
        return new AnalysisPeriodSpec(AnalysisPeriodPreset.EXPLICIT_RANGE,
            Objects.requireNonNull(start), Objects.requireNonNull(end), null);
    }

    public static AnalysisPeriodSpec lastNMonths(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("lastNMonths 必须为正整数");
        }
        return new AnalysisPeriodSpec(AnalysisPeriodPreset.LAST_N_MONTHS, null, null, count);
    }
}
