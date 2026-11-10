package com.liuqitech.accountingassistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Untrusted structured model output, validated before any draft reaches the client. */
public record ImageTransactionModelResult(List<Entry> transactions, List<String> warnings,
                                           boolean tooManyTransactions) {

    public record Entry(BigDecimal amount,
                        String type,
                        String merchant,
                        String description,
                        LocalDate transactionDate,
                        String note,
                        String relatedUser,
                        BigDecimal confidence,
                        Long categoryId,
                        Long parentCategoryId,
                        String accountName,
                        String currency,
                        String originalText) {
    }
}
