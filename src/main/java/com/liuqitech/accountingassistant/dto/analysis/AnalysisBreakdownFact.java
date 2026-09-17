package com.liuqitech.accountingassistant.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

public record AnalysisBreakdownFact(
        List<Row> rows,
        BigDecimal denominator,
        boolean associationShare) {
    public record Row(
            Long id,
            String label,
            String parentLabel,
            BigDecimal value,
            long count) {}
}
