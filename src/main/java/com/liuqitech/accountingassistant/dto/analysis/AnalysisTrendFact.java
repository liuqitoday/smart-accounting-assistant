package com.liuqitech.accountingassistant.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

public record AnalysisTrendFact(List<Point> points) {
    public record Point(
            String period,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal value,
            long count) {}
}
