package com.liuqitech.accountingassistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.AnalysisChatResponse;
import com.liuqitech.accountingassistant.dto.AnalysisMessageDto;
import com.liuqitech.accountingassistant.dto.PageResponseDto;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisContextSummary;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPayloadV2;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPeriod;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisQuery;
import com.liuqitech.accountingassistant.entity.AnalysisChatMessage;
import com.liuqitech.accountingassistant.enums.AnalysisDimension;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisOperationKind;
import com.liuqitech.accountingassistant.enums.AnalysisPeriodPreset;
import com.liuqitech.accountingassistant.enums.AnalysisQueryKind;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.AnalysisChatContextRepository;
import com.liuqitech.accountingassistant.repository.AnalysisChatMessageRepository;
import com.liuqitech.accountingassistant.repository.projection.AnalysisConversationLine;
import com.liuqitech.accountingassistant.service.analysis.AnalysisChatPersistenceService;
import com.liuqitech.accountingassistant.service.analysis.AnalysisContextService;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPromptBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalysisContextServiceTest {

    @Autowired
    private AnalysisChatPersistenceService persistence;
    @Autowired
    private AnalysisContextService contextService;
    @Autowired
    private AnalysisChatContextRepository contextRepository;
    @Autowired
    private AnalysisChatMessageRepository messageRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AnalysisPromptBuilder promptBuilder;

    @Test
    void successfulRoundUpdatesOnlySameLedgerAndUserContext() throws Exception {
        String payload = "{\"schemaVersion\":2,\"status\":\"OK\"}";
        persistence.saveRound(10L, "alice", "那去年呢", "去年支出 100 元", payload,
            AnalysisResponseStatus.OK, contextSummary());

        assertThat(contextRepository.findByLedgerIdAndUserId(10L, "alice")).isPresent();
        assertThat(contextRepository.findByLedgerIdAndUserId(10L, "bob")).isEmpty();
        assertThat(contextRepository.findByLedgerIdAndUserId(20L, "alice")).isEmpty();
    }

    @Test
    void clarificationOrFailureDoesNotOverwriteLastSuccessfulPlan() throws Exception {
        contextService.upsertSuccess(10L, "alice", contextSummary());

        persistence.saveRound(10L, "alice", "哪个最多", "请补充时间范围", null,
            AnalysisResponseStatus.CLARIFICATION_REQUIRED, null);

        assertThat(contextService.find(10L, "alice")).contains(contextSummary());
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisResponseStatus.class,
        names = {"OK", "NO_DATA", "PARTIAL_RESULT"})
    void displayableAnalysisStatusesUpdateTypedContext(AnalysisResponseStatus status) {
        persistence.saveRound(10L, "alice", "本月支出", "回答", validPayload(status),
            status, contextSummary());

        assertThat(contextService.find(10L, "alice")).contains(contextSummary());
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisResponseStatus.class,
        names = {"CLARIFICATION_REQUIRED", "INVALID_PLAN", "AI_UNAVAILABLE", "QUERY_FAILED"})
    void nonResultStatusesDoNotUpdateContext(AnalysisResponseStatus status) {
        persistence.saveRound(10L, "alice", "问题", "回答", validPayload(status), status, null);

        assertThat(contextService.find(10L, "alice")).isEmpty();
    }

    @Test
    void clearDeletesOnlySameUsersMessagesAndContext() {
        persistence.saveRound(10L, "alice", "问题 A", "回答 A", null,
            AnalysisResponseStatus.NO_DATA, null);
        persistence.saveRound(10L, "bob", "问题 B", "回答 B", null,
            AnalysisResponseStatus.NO_DATA, null);

        persistence.clear(10L, "alice");

        assertThat(messageRepository.findByLedgerIdAndUserId(10L, "alice", PageRequest.of(0, 20))).isEmpty();
        assertThat(contextRepository.findByLedgerIdAndUserId(10L, "alice")).isEmpty();
        assertThat(messageRepository.findByLedgerIdAndUserId(10L, "bob", PageRequest.of(0, 20))).isNotEmpty();
    }

    @Test
    void rateLimitedDoesNotPersistAnyMessage() {
        assertThatThrownBy(() -> persistence.saveRound(10L, "alice", "问题", "回答", null,
            AnalysisResponseStatus.RATE_LIMITED, null))
            .isInstanceOf(BusinessException.class);

        assertThat(messageRepository.findByLedgerIdAndUserId(10L, "alice", PageRequest.of(0, 20))).isEmpty();
        assertThat(contextRepository.findByLedgerIdAndUserId(10L, "alice")).isEmpty();
    }

    @Test
    void unavailableAndQueryFailedPersistAsFailedStatus() {
        persistence.saveRound(10L, "alice", "问题", "回答", validPayload(AnalysisResponseStatus.AI_UNAVAILABLE),
            AnalysisResponseStatus.AI_UNAVAILABLE, null);

        assertThat(messageRepository.findByLedgerIdAndUserId(10L, "alice", PageRequest.of(0, 20)).getContent())
            .extracting(AnalysisChatMessage::getStatus)
            .containsOnly("FAILED");
    }

    @Test
    void clarificationPersistsAsOkStatusAndMapsResponseDtos() {
        AnalysisChatResponse response = persistence.saveRound(10L, "alice", "问题", "回答",
            validPayload(AnalysisResponseStatus.CLARIFICATION_REQUIRED),
            AnalysisResponseStatus.CLARIFICATION_REQUIRED, null);

        assertThat(response.getUserMessage().getRole()).isEqualTo("USER");
        assertThat(response.getUserMessage().getContent()).isEqualTo("问题");
        assertThat(response.getUserMessage().getStatus()).isEqualTo("OK");
        assertThat(response.getUserMessage().getId()).isNotNull();
        assertThat(response.getAssistantMessage().getRole()).isEqualTo("ASSISTANT");
        assertThat(response.getAssistantMessage().getContent()).isEqualTo("回答");
        assertThat(response.getAssistantMessage().getStatus()).isEqualTo("OK");
        assertThat(messageRepository.findByLedgerIdAndUserId(10L, "alice", PageRequest.of(0, 20)).getContent())
            .extracting(AnalysisChatMessage::getStatus)
            .containsOnly("OK");
    }

    @Test
    void upsertSuccessClampsResultSummaryKeysAndValues() {
        Map<String, String> oversized = new LinkedHashMap<>();
        oversized.put("a".repeat(50), "b".repeat(200));
        for (int i = 0; i < 25; i++) {
            oversized.put("k" + i, "v" + i);
        }
        AnalysisContextSummary summary = new AnalysisContextSummary(
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
            new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)),
            TransactionType.EXPENSE, AnalysisMetric.SUM, AnalysisDimension.PARENT_CATEGORY,
            AnalysisFilters.empty(), AnalysisResultKind.BREAKDOWN, oversized);

        contextService.upsertSuccess(10L, "alice", summary);

        AnalysisContextSummary found = contextService.find(10L, "alice").orElseThrow();
        assertThat(found.resultSummary()).hasSizeLessThanOrEqualTo(20);
        found.resultSummary().forEach((key, value) -> {
            assertThat(key.length()).isLessThanOrEqualTo(40);
            assertThat(value.length()).isLessThanOrEqualTo(120);
        });
    }

    @Test
    void getMessagesClampsPageAndSize() {
        persistence.saveRound(10L, "alice", "问题", "回答", null,
            AnalysisResponseStatus.OK, contextSummary());

        PageResponseDto<AnalysisMessageDto> page = persistence.getMessages(10L, "alice", -1, 1_000_000);

        assertThat(page.getNumber()).isEqualTo(0);
        assertThat(page.getSize()).isLessThanOrEqualTo(50);
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    void findRecentConversationLinesSelectsOnlyOkRoleAndContent() {
        persistence.saveRound(10L, "alice", "成功问题", "成功回答", null,
            AnalysisResponseStatus.OK, contextSummary());
        persistence.saveRound(10L, "alice", "失败问题", "失败回答", null,
            AnalysisResponseStatus.AI_UNAVAILABLE, null);

        List<AnalysisConversationLine> lines = messageRepository.findRecentConversationLines(
            10L, "alice", PageRequest.of(0, 10));

        assertThat(lines).extracting(AnalysisConversationLine::getRole)
            .containsExactly("ASSISTANT", "USER");
        assertThat(lines).extracting(AnalysisConversationLine::getContent)
            .containsExactly("成功回答", "成功问题");
    }

    @Test
    void promptBuilderReturnsCappedOptionSnippet() {
        String snippet = promptBuilder.buildOptionSnippet(10L);

        assertThat(snippet).contains("可用账户").contains("可用标签").contains("可用分类");
        assertThat(promptBuilder.buildPlannerRules())
            .contains("operation: AGGREGATE")
            .contains("CURRENT_MONTH")
            .contains("\"kind\":\"AGGREGATE\"")
            .contains("禁止把 JSON Schema 里的类型名");
    }

    private AnalysisContextSummary contextSummary() {
        return new AnalysisContextSummary(
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
            new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)),
            TransactionType.EXPENSE, AnalysisMetric.SUM, AnalysisDimension.PARENT_CATEGORY,
            AnalysisFilters.empty(), AnalysisResultKind.BREAKDOWN,
            Map.of("topLabel", "生活", "topValue", "100.00"));
    }

    private String validPayload(AnalysisResponseStatus status) {
        try {
            RawAnalysisPlan raw = new RawAnalysisPlan();
            raw.setOperation("AGGREGATE");
            raw.setTitle("支出合计");
            RawAnalysisQuery query = new RawAnalysisQuery();
            query.setId("q1");
            query.setKind("AGGREGATE");
            query.setMetric("SUM");
            query.setTransactionType("EXPENSE");
            query.setTitle("支出合计");
            RawAnalysisPeriod period = new RawAnalysisPeriod();
            period.setPreset("CURRENT_MONTH");
            query.setPeriod(period);
            raw.setQueries(List.of(query));

            NormalizedAnalysisPlan normalized = new NormalizedAnalysisPlan(
                AnalysisOperationKind.AGGREGATE,
                null,
                List.of(new NormalizedAnalysisQuery(
                    "q1",
                    AnalysisQueryKind.AGGREGATE,
                    AnalysisMetric.SUM,
                    TransactionType.EXPENSE,
                    AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH),
                    new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25)),
                    AnalysisFilters.empty(),
                    null,
                    null,
                    AnalysisSortField.AMOUNT,
                    AnalysisSortDirection.DESC,
                    10,
                    "支出合计")),
                "支出合计");

            return objectMapper.writeValueAsString(new AnalysisPayloadV2(
                2, raw, normalized, List.of(), status, List.of(), List.of()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
