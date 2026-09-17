package com.liuqitech.accountingassistant.dto.analysis;

import com.liuqitech.accountingassistant.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnalysisTransactionFact(
        Long id,
        BigDecimal amount,
        TransactionType type,
        LocalDate date,
        String description,
        String category,
        String parentCategory,
        String account,
        String merchant) {}
