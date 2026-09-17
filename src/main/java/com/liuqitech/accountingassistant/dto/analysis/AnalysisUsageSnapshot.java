package com.liuqitech.accountingassistant.dto.analysis;

import java.time.LocalDate;

public record AnalysisUsageSnapshot(
        LocalDate businessDate,
        long successfulCount,
        long clarificationCount,
        long failedCount) {}
