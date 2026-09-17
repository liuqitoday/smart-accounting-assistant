package com.liuqitech.accountingassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.AnalysisChatResponse;
import com.liuqitech.accountingassistant.dto.AnalysisMessageDto;
import com.liuqitech.accountingassistant.dto.PageResponseDto;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisContextSummary;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisExecutionOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFollowUp;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPayloadV2;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPlanValidationResult;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResultPeriod;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisUsageOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisUsageSnapshot;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPeriod;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.result.AggregateResult;
import com.liuqitech.accountingassistant.entity.AnalysisChatMessage;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisOperationKind;
import com.liuqitech.accountingassistant.enums.AnalysisPeriodPreset;
import com.liuqitech.accountingassistant.enums.AnalysisQueryKind;
import com.liuqitech.accountingassistant.enums.AnalysisRequestClass;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.enums.AnalysisResultKind;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.AnalysisUnit;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.AnalysisChatMessageRepository;
import com.liuqitech.accountingassistant.service.analysis.AnalysisChatPersistenceService;
import com.liuqitech.accountingassistant.service.analysis.AnalysisContextService;
import com.liuqitech.accountingassistant.service.analysis.AnalysisOperationExecutor;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPayloadFactory;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPlanValidator;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPromptBuilder;
import com.liuqitech.accountingassistant.service.analysis.AnalysisResponseComposer;
import com.liuqitech.accountingassistant.service.analysis.AnalysisUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisChatServiceTest {

    private static final Long LEDGER_ID = 100L;
    private static final String USERNAME = "alice";

    @Mock private AnalysisUsageService usageService;
    @Mock private AnalysisContextService contextService;
    @Mock private AnalysisChatMessageRepository messageRepository;
    @Mock private AnalysisPromptBuilder promptBuilder;
    @Mock private AIParserService aiParserService;
    @Mock private AnalysisPlanValidator planValidator;
    @Mock private AnalysisOperationExecutor operationExecutor;
    @Mock private AnalysisResponseComposer responseComposer;
    @Mock private AnalysisPayloadFactory payloadFactory;
    @Mock private AnalysisChatPersistenceService persistenceService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private AnalysisChatService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisChatService(
            usageService,
            contextService,
            messageRepository,
            promptBuilder,
            aiParserService,
            planValidator,
            operationExecutor,
            responseComposer,
            payloadFactory,
            persistenceService,
            objectMapper);
        lenient().when(promptBuilder.buildPlannerRules()).thenReturn("operation: AGGREGATE\nperiod.preset: CURRENT_MONTH\n");
        lenient().when(promptBuilder.buildOptionSnippet(LEDGER_ID)).thenReturn("可用账户\n");
        lenient().when(contextService.find(LEDGER_ID, USERNAME)).thenReturn(Optional.empty());
        lenient().when(messageRepository.findRecentConversationLines(eq(LEDGER_ID), eq(USERNAME), any(Pageable.class)))
            .thenReturn(List.of());
        lenient().when(usageService.getToday(USERNAME))
            .thenReturn(new AnalysisUsageSnapshot(LocalDate.of(2026, 8, 25), 0, 0, 0));
    }

    @Test
    void chatReturnsSchemaVersionTwoAndStoresNormalizedPlanAndFollowUps() throws Exception {
        RawAnalysisPlan raw = validRawPlan();
        NormalizedAnalysisPlan normalized = validNormalizedPlan();
        AnalysisExecutionOutcome outcome = okAggregateOutcome();
        AnalysisPayloadV2 payload = new AnalysisPayloadV2(
            2, raw, normalized, outcome.results(), AnalysisResponseStatus.OK, List.of(),
            List.of(new AnalysisFollowUp("查看明细", "查看明细")));
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class))).thenReturn(raw);
        when(planValidator.validate(eq(LEDGER_ID), eq(raw), any(LocalDate.class)))
            .thenReturn(AnalysisPlanValidationResult.valid(normalized));
        when(operationExecutor.execute(LEDGER_ID, normalized)).thenReturn(outcome);
        when(responseComposer.compose(outcome)).thenReturn("本月支出 100.00 元。");
        when(payloadFactory.create(raw, normalized, outcome)).thenReturn(payload);
        when(persistenceService.saveRound(eq(LEDGER_ID), eq(USERNAME), anyString(), anyString(), anyString(),
            eq(AnalysisResponseStatus.OK), any(AnalysisContextSummary.class)))
            .thenReturn(response("本月支出和上月比", "本月支出 100.00 元。",
                objectMapper.writeValueAsString(payload), "OK"));

        AnalysisChatResponse result = service.chat(LEDGER_ID, USERNAME, "本月支出和上月比");
        JsonNode json = objectMapper.readTree(result.getAssistantMessage().getPayload());

        assertThat(json.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(json.path("normalizedPlan").isObject()).isTrue();
        assertThat(json.path("followUps").isArray()).isTrue();
        verify(usageService).checkDailyAllowance(USERNAME, AnalysisRequestClass.FORMAL);
        verify(usageService).record(eq(USERNAME), any(AnalysisUsageOutcome.class));
        verify(persistenceService).saveRound(eq(LEDGER_ID), eq(USERNAME), eq("本月支出和上月比"),
            eq("本月支出 100.00 元。"), anyString(), eq(AnalysisResponseStatus.OK), any());
    }

    @Test
    void plannerPromptIncludesAllowedEnumValues() {
        RawAnalysisPlan raw = validRawPlan();
        NormalizedAnalysisPlan normalized = validNormalizedPlan();
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class))).thenReturn(raw);
        when(planValidator.validate(eq(LEDGER_ID), eq(raw), any(LocalDate.class)))
            .thenReturn(AnalysisPlanValidationResult.valid(normalized));
        when(operationExecutor.execute(LEDGER_ID, normalized)).thenReturn(okAggregateOutcome());
        when(responseComposer.compose(any())).thenReturn("本月支出 100.00 元。");
        when(payloadFactory.create(eq(raw), eq(normalized), any())).thenReturn(new AnalysisPayloadV2(
            2, raw, normalized, List.of(), AnalysisResponseStatus.OK, List.of(), List.of()));
        when(persistenceService.saveRound(eq(LEDGER_ID), eq(USERNAME), anyString(), anyString(), any(),
            eq(AnalysisResponseStatus.OK), any()))
            .thenReturn(response("本月花了多少钱", "本月支出 100.00 元。", "{}", "OK"));

        service.chat(LEDGER_ID, USERNAME, "本月花了多少钱");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiParserService).parseStructured(prompt.capture(), eq(RawAnalysisPlan.class));
        assertThat(prompt.getValue())
            .contains("operation: AGGREGATE")
            .contains("CURRENT_MONTH")
            .contains("本月花了多少钱");
    }

    @Test
    void clarificationDoesNotConsumeFormalQuotaOrContext() {
        RawAnalysisPlan raw = new RawAnalysisPlan();
        raw.setOperation("AGGREGATE");
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class))).thenReturn(raw);
        when(planValidator.validate(eq(LEDGER_ID), eq(raw), any(LocalDate.class)))
            .thenReturn(AnalysisPlanValidationResult.clarification(List.of("请补充时间范围")));
        when(persistenceService.saveRound(eq(LEDGER_ID), eq(USERNAME), eq("哪个最多"), anyString(),
            any(), eq(AnalysisResponseStatus.CLARIFICATION_REQUIRED), isNull()))
            .thenReturn(response("哪个最多", "请补充时间范围", null, "OK"));

        service.chat(LEDGER_ID, USERNAME, "哪个最多");

        ArgumentCaptor<AnalysisUsageOutcome> usage = ArgumentCaptor.forClass(AnalysisUsageOutcome.class);
        verify(usageService).record(eq(USERNAME), usage.capture());
        assertThat(usage.getValue().status()).isEqualTo(AnalysisResponseStatus.CLARIFICATION_REQUIRED);
        verify(usageService).checkDailyAllowance(USERNAME, AnalysisRequestClass.CLARIFICATION);
        verify(operationExecutor, never()).execute(anyLong(), any());
        verify(persistenceService).saveRound(eq(LEDGER_ID), eq(USERNAME), eq("哪个最多"), anyString(),
            any(), eq(AnalysisResponseStatus.CLARIFICATION_REQUIRED), isNull());
        verify(persistenceService, never()).saveRound(anyLong(), anyString(), anyString(), anyString(),
            any(), eq(AnalysisResponseStatus.OK), any());
    }

    @Test
    void v1HistoryPayloadStillReturnsUnchangedAndV2HistoryParsesTogether() {
        AnalysisMessageDto v1 = new AnalysisMessageDto(1L, "ASSISTANT", "旧结果",
            "{\"plan\":{},\"results\":{\"queries\":[]}}", "OK", LocalDateTime.of(2026, 8, 25, 12, 0));
        AnalysisMessageDto v2 = new AnalysisMessageDto(2L, "ASSISTANT", "新结果",
            "{\"schemaVersion\":2,\"results\":[],\"status\":\"NO_DATA\"}", "OK",
            LocalDateTime.of(2026, 8, 25, 12, 1));
        when(persistenceService.getMessages(LEDGER_ID, USERNAME, 0, 20))
            .thenReturn(new PageResponseDto<>(List.of(v1, v2), 2, 1, 20, 0, true, true));

        PageResponseDto<AnalysisMessageDto> page = service.getMessages(LEDGER_ID, USERNAME, 0, 20);

        assertThat(page.getContent()).extracting(AnalysisMessageDto::getPayload)
            .contains("{\"plan\":{},\"results\":{\"queries\":[]}}",
                "{\"schemaVersion\":2,\"results\":[],\"status\":\"NO_DATA\"}");
        verify(persistenceService).getMessages(LEDGER_ID, USERNAME, 0, 20);
    }

    @Test
    void rateLimitThrowsBusinessExceptionWithoutSaving() {
        doThrow(new BusinessException("提问过于频繁，请稍后再试", "RATE_LIMITED"))
            .when(usageService).checkRequestWindow(USERNAME);

        assertThrows(BusinessException.class, () -> service.chat(LEDGER_ID, USERNAME, "本月支出"));
        verify(persistenceService, never()).saveRound(anyLong(), anyString(), anyString(), anyString(),
            any(), any(), any());
        verify(aiParserService, never()).parseStructured(anyString(), any());
    }

    @Test
    void modelFailureMapsToAiUnavailable() {
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class)))
            .thenThrow(new RuntimeException("AI 超时"));
        when(persistenceService.saveRound(eq(LEDGER_ID), eq(USERNAME), eq("本月支出"), anyString(),
            isNull(), eq(AnalysisResponseStatus.AI_UNAVAILABLE), isNull()))
            .thenReturn(response("本月支出", "AI 服务暂时不可用，请稍后重试", null, "FAILED"));

        AnalysisChatResponse result = service.chat(LEDGER_ID, USERNAME, "本月支出");

        assertThat(result.getAssistantMessage().getStatus()).isEqualTo("FAILED");
        verify(usageService).record(eq(USERNAME), any(AnalysisUsageOutcome.class));
        verify(operationExecutor, never()).execute(anyLong(), any());
    }

    @Test
    void queryFailureMapsToQueryFailed() {
        RawAnalysisPlan raw = validRawPlan();
        NormalizedAnalysisPlan normalized = validNormalizedPlan();
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class))).thenReturn(raw);
        when(planValidator.validate(eq(LEDGER_ID), eq(raw), any(LocalDate.class)))
            .thenReturn(AnalysisPlanValidationResult.valid(normalized));
        when(operationExecutor.execute(LEDGER_ID, normalized))
            .thenThrow(new RuntimeException("SQL 错误"));
        when(persistenceService.saveRound(eq(LEDGER_ID), eq(USERNAME), eq("本月支出"), anyString(),
            isNull(), eq(AnalysisResponseStatus.QUERY_FAILED), isNull()))
            .thenReturn(response("本月支出", "查询失败，请稍后重试", null, "FAILED"));

        AnalysisChatResponse result = service.chat(LEDGER_ID, USERNAME, "本月支出");

        assertThat(result.getAssistantMessage().getStatus()).isEqualTo("FAILED");
        ArgumentCaptor<AnalysisUsageOutcome> usage = ArgumentCaptor.forClass(AnalysisUsageOutcome.class);
        verify(usageService).record(eq(USERNAME), usage.capture());
        assertThat(usage.getValue().status()).isEqualTo(AnalysisResponseStatus.QUERY_FAILED);
    }

    @Test
    void clearMessagesDelegatesToPersistence() {
        service.clearMessages(LEDGER_ID, USERNAME);
        verify(persistenceService).clear(LEDGER_ID, USERNAME);
    }

    private static RawAnalysisPlan validRawPlan() {
        RawAnalysisPeriod period = new RawAnalysisPeriod();
        period.setPreset("CURRENT_MONTH");
        RawAnalysisQuery query = new RawAnalysisQuery();
        query.setId("q1");
        query.setKind("AGGREGATE");
        query.setMetric("SUM");
        query.setTransactionType("EXPENSE");
        query.setPeriod(period);
        query.setTitle("本月支出");
        RawAnalysisPlan plan = new RawAnalysisPlan();
        plan.setOperation("AGGREGATE");
        plan.setTitle("本月支出");
        plan.setQueries(List.of(query));
        return plan;
    }

    private static NormalizedAnalysisPlan validNormalizedPlan() {
        AnalysisDateRange range = new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25));
        NormalizedAnalysisQuery query = new NormalizedAnalysisQuery(
            "q1", AnalysisQueryKind.AGGREGATE, AnalysisMetric.SUM, TransactionType.EXPENSE,
            AnalysisPeriodSpec.of(AnalysisPeriodPreset.CURRENT_MONTH), range, AnalysisFilters.empty(),
            null, null, AnalysisSortField.AMOUNT, AnalysisSortDirection.DESC, 10, "本月支出");
        return new NormalizedAnalysisPlan(AnalysisOperationKind.AGGREGATE, null, List.of(query), "本月支出");
    }

    private static AnalysisExecutionOutcome okAggregateOutcome() {
        AnalysisDateRange range = new AnalysisDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25));
        AggregateResult result = new AggregateResult(
            "本月支出", AnalysisMetric.SUM, AnalysisUnit.CNY, TransactionType.EXPENSE,
            new AnalysisResultPeriod(range, null), AnalysisFilters.empty(), List.of(),
            new AggregateResult.Values(new BigDecimal("100.00"), 1L));
        return new AnalysisExecutionOutcome(AnalysisResponseStatus.OK, List.of(result), List.of());
    }

    private static AnalysisChatResponse response(String question, String answer, String payload, String status) {
        return new AnalysisChatResponse(
            new AnalysisMessageDto(1L, "USER", question, null, status, LocalDateTime.now()),
            new AnalysisMessageDto(2L, "ASSISTANT", answer, payload, status, LocalDateTime.now()));
    }
}
