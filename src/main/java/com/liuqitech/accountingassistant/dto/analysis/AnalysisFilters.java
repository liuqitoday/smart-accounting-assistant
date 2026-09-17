package com.liuqitech.accountingassistant.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

public record AnalysisFilters(
        Long categoryId,
        Long accountId,
        List<Long> tagIds,
        String merchant,
        String keyword,
        BigDecimal minAmount,
        BigDecimal maxAmount) {

    public AnalysisFilters {
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
    }

    public static AnalysisFilters empty() {
        return new AnalysisFilters(null, null, List.of(), null, null, null, null);
    }
}
