package com.liuqitech.accountingassistant.dto.analysis;

import java.math.BigDecimal;

public record AnalysisAggregateFact(BigDecimal amount, long count) {}
