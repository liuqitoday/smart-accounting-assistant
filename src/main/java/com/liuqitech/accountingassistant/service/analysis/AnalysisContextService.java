package com.liuqitech.accountingassistant.service.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisContextSummary;
import com.liuqitech.accountingassistant.entity.AnalysisChatContext;
import com.liuqitech.accountingassistant.repository.AnalysisChatContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AnalysisContextService {

    static final int MAX_RESULT_SUMMARY_KEYS = 20;
    static final int MAX_RESULT_SUMMARY_KEY_CHARS = 40;
    static final int MAX_RESULT_SUMMARY_VALUE_CHARS = 120;

    private final AnalysisChatContextRepository contextRepository;
    private final ObjectMapper objectMapper;

    public AnalysisContextService(AnalysisChatContextRepository contextRepository, ObjectMapper objectMapper) {
        this.contextRepository = contextRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisContextSummary> find(Long ledgerId, String userId) {
        return contextRepository.findByLedgerIdAndUserId(ledgerId, userId)
            .map(this::deserialize);
    }

    @Transactional
    public void upsertSuccess(Long ledgerId, String userId, AnalysisContextSummary summary) {
        AnalysisContextSummary clamped = clamp(summary);
        String json = serialize(clamped);
        AnalysisChatContext entity = contextRepository.findByLedgerIdAndUserId(ledgerId, userId)
            .orElseGet(() -> {
                AnalysisChatContext created = new AnalysisChatContext();
                created.setLedgerId(ledgerId);
                created.setUserId(userId);
                return created;
            });
        entity.setSummaryJson(json);
        contextRepository.save(entity);
    }

    private AnalysisContextSummary clamp(AnalysisContextSummary summary) {
        if (summary == null) {
            throw new IllegalArgumentException("summary");
        }
        Map<String, String> source = summary.resultSummary() == null ? Map.of() : summary.resultSummary();
        Map<String, String> clamped = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (clamped.size() >= MAX_RESULT_SUMMARY_KEYS) {
                break;
            }
            String key = entry.getKey() == null ? "" : truncate(entry.getKey(), MAX_RESULT_SUMMARY_KEY_CHARS);
            String value = entry.getValue() == null ? "" : truncate(entry.getValue(), MAX_RESULT_SUMMARY_VALUE_CHARS);
            if (!key.isEmpty()) {
                clamped.put(key, value);
            }
        }
        return new AnalysisContextSummary(
            summary.periodSemantic(),
            summary.resolvedPeriod(),
            summary.transactionType(),
            summary.metric(),
            summary.dimension(),
            summary.filters(),
            summary.resultKind(),
            clamped);
    }

    private static String truncate(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private String serialize(AnalysisContextSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化分析上下文失败", e);
        }
    }

    private AnalysisContextSummary deserialize(AnalysisChatContext entity) {
        try {
            return objectMapper.readValue(entity.getSummaryJson(), AnalysisContextSummary.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("反序列化分析上下文失败", e);
        }
    }
}
