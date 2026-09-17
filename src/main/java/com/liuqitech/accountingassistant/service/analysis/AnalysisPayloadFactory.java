package com.liuqitech.accountingassistant.service.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisExecutionOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFollowUp;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPayloadV2;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResult;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTransactionFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisWarning;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.result.TransactionsResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AnalysisPayloadFactory {

    private static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    private static final int TEXT_TRUNCATE_CHARS = 120;
    private static final AnalysisWarning PAYLOAD_TOO_LARGE =
        new AnalysisWarning("PAYLOAD_TOO_LARGE", "结果过大，已省略交易明细");

    private final ObjectMapper objectMapper;
    private final AnalysisFollowUpService followUpService;

    public AnalysisPayloadFactory(ObjectMapper objectMapper, AnalysisFollowUpService followUpService) {
        this.objectMapper = objectMapper;
        this.followUpService = followUpService;
    }

    public AnalysisPayloadV2 create(RawAnalysisPlan raw, NormalizedAnalysisPlan normalized,
                                    AnalysisExecutionOutcome outcome) {
        AnalysisExecutionOutcome safeOutcome = outcome == null
            ? new AnalysisExecutionOutcome(null, List.of(), List.of())
            : outcome;
        List<AnalysisFollowUp> followUps = followUpService.suggest(normalized, safeOutcome);
        AnalysisPayloadV2 payload = new AnalysisPayloadV2(
            2,
            raw,
            normalized,
            safeOutcome.results(),
            safeOutcome.status(),
            safeOutcome.warnings(),
            followUps);
        return shrinkToLimit(payload);
    }

    private AnalysisPayloadV2 shrinkToLimit(AnalysisPayloadV2 payload) {
        if (utf8Size(payload) <= MAX_PAYLOAD_BYTES) {
            return payload;
        }

        AnalysisPayloadV2 current = rebuild(payload, truncateTexts(payload.results()), payload.warnings());
        while (utf8Size(current) > MAX_PAYLOAD_BYTES && hasDisplayedTransactions(current.results())) {
            current = rebuild(current, dropLastTransaction(current.results()), current.warnings());
        }
        if (utf8Size(current) <= MAX_PAYLOAD_BYTES && !hadOversizedTransactionText(payload.results())) {
            return current;
        }

        List<AnalysisWarning> payloadWarnings = withPayloadTooLarge(payload.warnings());
        AnalysisPayloadV2 summary = rebuild(payload, omitTransactionDetails(payload.results()), payloadWarnings);
        if (utf8Size(summary) > MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("V2 payload summary exceeds 256 KiB");
        }
        return summary;
    }

    private static List<AnalysisResult> truncateTexts(List<AnalysisResult> results) {
        List<AnalysisResult> next = new ArrayList<>(results.size());
        for (AnalysisResult result : results) {
            if (result instanceof TransactionsResult transactions) {
                List<AnalysisTransactionFact> truncated = transactions.transactions().stream()
                    .map(AnalysisPayloadFactory::truncateFactText)
                    .toList();
                next.add(copyTransactions(transactions, transactions.warnings(),
                    new TransactionsResult.Values(
                        transactions.values().totalCount(),
                        truncated.size(),
                        truncated.size() < transactions.values().totalCount()),
                    truncated));
            } else {
                next.add(result);
            }
        }
        return next;
    }

    private static List<AnalysisResult> dropLastTransaction(List<AnalysisResult> results) {
        int index = lastNonEmptyTransactionsIndex(results);
        if (index < 0) {
            return results;
        }
        TransactionsResult transactions = (TransactionsResult) results.get(index);
        List<AnalysisTransactionFact> remaining = new ArrayList<>(transactions.transactions());
        remaining.remove(remaining.size() - 1);
        List<AnalysisResult> next = new ArrayList<>(results);
        next.set(index, copyTransactions(transactions, transactions.warnings(),
            new TransactionsResult.Values(
                transactions.values().totalCount(),
                remaining.size(),
                remaining.size() < transactions.values().totalCount()),
            remaining));
        return next;
    }

    private static List<AnalysisResult> omitTransactionDetails(List<AnalysisResult> results) {
        List<AnalysisResult> next = new ArrayList<>(results.size());
        for (AnalysisResult result : results) {
            if (result instanceof TransactionsResult transactions) {
                next.add(copyTransactions(
                    transactions,
                    withPayloadTooLarge(transactions.warnings()),
                    new TransactionsResult.Values(transactions.values().totalCount(), 0, true),
                    List.of()));
            } else {
                next.add(result);
            }
        }
        return next;
    }

    private static TransactionsResult copyTransactions(TransactionsResult original,
                                                       List<AnalysisWarning> warnings,
                                                       TransactionsResult.Values values,
                                                       List<AnalysisTransactionFact> transactions) {
        return new TransactionsResult(
            original.title(),
            original.metric(),
            original.unit(),
            original.transactionType(),
            original.period(),
            original.filters(),
            warnings,
            values,
            transactions);
    }

    private static AnalysisPayloadV2 rebuild(AnalysisPayloadV2 source,
                                             List<AnalysisResult> results,
                                             List<AnalysisWarning> warnings) {
        return new AnalysisPayloadV2(
            source.schemaVersion(),
            source.rawPlan(),
            source.normalizedPlan(),
            results,
            source.status(),
            warnings,
            source.followUps());
    }

    private static List<AnalysisWarning> withPayloadTooLarge(List<AnalysisWarning> warnings) {
        List<AnalysisWarning> next = new ArrayList<>(warnings == null ? List.of() : warnings);
        if (next.stream().noneMatch(warning -> "PAYLOAD_TOO_LARGE".equals(warning.code()))) {
            next.add(PAYLOAD_TOO_LARGE);
        }
        return next;
    }

    private static AnalysisTransactionFact truncateFactText(AnalysisTransactionFact fact) {
        return new AnalysisTransactionFact(
            fact.id(),
            fact.amount(),
            fact.type(),
            fact.date(),
            truncate(fact.description()),
            fact.category(),
            fact.parentCategory(),
            fact.account(),
            truncate(fact.merchant()));
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= TEXT_TRUNCATE_CHARS) {
            return value;
        }
        return value.substring(0, TEXT_TRUNCATE_CHARS);
    }

    private static boolean hasDisplayedTransactions(List<AnalysisResult> results) {
        for (AnalysisResult result : results) {
            if (result instanceof TransactionsResult transactions && !transactions.transactions().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hadOversizedTransactionText(List<AnalysisResult> results) {
        for (AnalysisResult result : results) {
            if (result instanceof TransactionsResult transactions) {
                for (AnalysisTransactionFact fact : transactions.transactions()) {
                    if (isOversized(fact.description()) || isOversized(fact.merchant())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isOversized(String value) {
        return value != null && value.length() > TEXT_TRUNCATE_CHARS;
    }

    private static int lastNonEmptyTransactionsIndex(List<AnalysisResult> results) {
        for (int i = results.size() - 1; i >= 0; i--) {
            if (results.get(i) instanceof TransactionsResult transactions
                && !transactions.transactions().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int utf8Size(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize analysis payload", e);
        }
    }
}
