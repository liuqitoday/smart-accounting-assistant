package com.liuqitech.accountingassistant.dto.analysis;

import java.time.LocalDate;
import java.util.Objects;

public record AnalysisDateRange(LocalDate start, LocalDate end) {
    public AnalysisDateRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start 不能晚于 end");
        }
    }
}
