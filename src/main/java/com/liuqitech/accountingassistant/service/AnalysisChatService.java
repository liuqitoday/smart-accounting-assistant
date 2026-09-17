package com.liuqitech.accountingassistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.AnalysisChatResponse;
import com.liuqitech.accountingassistant.dto.AnalysisMessageDto;
import com.liuqitech.accountingassistant.dto.PageResponseDto;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisContextSummary;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisExecutionOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPayloadV2;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPlanValidationResult;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisUsageOutcome;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPlan;
import com.liuqitech.accountingassistant.enums.AnalysisRequestClass;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.AnalysisChatMessageRepository;
import com.liuqitech.accountingassistant.repository.projection.AnalysisConversationLine;
import com.liuqitech.accountingassistant.service.analysis.AnalysisChatPersistenceService;
import com.liuqitech.accountingassistant.service.analysis.AnalysisContextService;
import com.liuqitech.accountingassistant.service.analysis.AnalysisOperationExecutor;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPayloadFactory;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPlanValidator;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPromptBuilder;
import com.liuqitech.accountingassistant.service.analysis.AnalysisResponseComposer;
import com.liuqitech.accountingassistant.service.analysis.AnalysisUsageService;
import com.liuqitech.accountingassistant.util.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AnalysisChatService {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisChatService.class);
    private static final int CONTEXT_WINDOW = 5;

    private final AnalysisUsageService usageService;
    private final AnalysisContextService contextService;
    private final AnalysisChatMessageRepository messageRepository;
    private final AnalysisPromptBuilder promptBuilder;
    private final AIParserService aiParserService;
    private final AnalysisPlanValidator planValidator;
    private final AnalysisOperationExecutor operationExecutor;
    private final AnalysisResponseComposer responseComposer;
    private final AnalysisPayloadFactory payloadFactory;
    private final AnalysisChatPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    public AnalysisChatService(
            AnalysisUsageService usageService,
            AnalysisContextService contextService,
            AnalysisChatMessageRepository messageRepository,
            AnalysisPromptBuilder promptBuilder,
            AIParserService aiParserService,
            AnalysisPlanValidator planValidator,
            AnalysisOperationExecutor operationExecutor,
            AnalysisResponseComposer responseComposer,
            AnalysisPayloadFactory payloadFactory,
            AnalysisChatPersistenceService persistenceService,
            ObjectMapper objectMapper) {
        this.usageService = usageService;
        this.contextService = contextService;
        this.messageRepository = messageRepository;
        this.promptBuilder = promptBuilder;
        this.aiParserService = aiParserService;
        this.planValidator = planValidator;
        this.operationExecutor = operationExecutor;
        this.responseComposer = responseComposer;
        this.payloadFactory = payloadFactory;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }

    public AnalysisChatResponse chat(Long ledgerId, String username, String question) {
        Instant started = Instant.now();
        usageService.checkRequestWindow(username);
        usageService.checkFailureWindow(username);

        Optional<AnalysisContextSummary> priorContext = contextService.find(ledgerId, username);
        List<AnalysisConversationLine> recent = messageRepository.findRecentConversationLines(
            ledgerId, username, PageRequest.of(0, CONTEXT_WINDOW * 2));
        String prompt = buildPlannerPrompt(ledgerId, question, priorContext, recent);

        RawAnalysisPlan raw;
        try {
            raw = aiParserService.parseStructured(prompt, RawAnalysisPlan.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("AI① 解析失败 ledgerId={} userId={}", ledgerId, username, e);
            return persistTerminal(ledgerId, username, question, "AI 服务暂时不可用，请稍后重试",
                AnalysisResponseStatus.AI_UNAVAILABLE, started, 0, 0);
        }

        AnalysisRequestClass requestClass = classifyRequest(raw);
        usageService.checkDailyAllowance(username, requestClass);

        AnalysisPlanValidationResult validation;
        try {
            validation = planValidator.validate(ledgerId, raw, AppClock.today());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("分析计划校验失败 ledgerId={} userId={}", ledgerId, username, e);
            return persistTerminal(ledgerId, username, question, "分析计划无法处理，请换个问法",
                AnalysisResponseStatus.INVALID_PLAN, started, 0, 0);
        }

        if (validation.status() == AnalysisResponseStatus.CLARIFICATION_REQUIRED
                || validation.status() == AnalysisResponseStatus.INVALID_PLAN) {
            String answer = validation.errors().isEmpty()
                ? "请补充分析所需的时间或筛选条件。"
                : String.join("；", validation.errors());
            String payload = serializePayload(payloadFactory.create(raw, null,
                new AnalysisExecutionOutcome(validation.status(), List.of(), List.of())));
            AnalysisChatResponse response = persistenceService.saveRound(
                ledgerId, username, question, answer, payload, validation.status(), null);
            usageService.record(username, usageOutcome(validation.status(), started, 0, 0));
            logger.info("analysis chat status={} queries=0 rows=0", validation.status());
            return response;
        }

        NormalizedAnalysisPlan normalized = validation.normalizedPlan();
        AnalysisExecutionOutcome outcome;
        try {
            outcome = operationExecutor.execute(ledgerId, normalized);
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            logger.error("分析查询失败 ledgerId={} userId={}", ledgerId, username, e);
            return persistTerminal(ledgerId, username, question, "查询失败，请稍后重试",
                AnalysisResponseStatus.QUERY_FAILED, started, queryCount(normalized), 0);
        } catch (Exception e) {
            logger.error("分析执行失败 ledgerId={} userId={}", ledgerId, username, e);
            return persistTerminal(ledgerId, username, question, "查询失败，请稍后重试",
                AnalysisResponseStatus.QUERY_FAILED, started, queryCount(normalized), 0);
        }

        String answer = responseComposer.compose(outcome);
        AnalysisPayloadV2 payload = payloadFactory.create(raw, normalized, outcome);
        AnalysisContextSummary successContext = contextUpdateStatuses(outcome.status())
            ? toContextSummary(normalized, outcome)
            : null;
        AnalysisChatResponse response = persistenceService.saveRound(
            ledgerId, username, question, answer, serializePayload(payload), outcome.status(), successContext);
        usageService.record(username, usageOutcome(outcome.status(), started,
            queryCount(normalized), resultRowCount(outcome)));
        logger.info("analysis chat status={} queries={} rows={}",
            outcome.status(), queryCount(normalized), resultRowCount(outcome));
        return response;
    }

    public PageResponseDto<AnalysisMessageDto> getMessages(Long ledgerId, String username, int page, int size) {
        return persistenceService.getMessages(ledgerId, username, page, size);
    }

    public void clearMessages(Long ledgerId, String username) {
        persistenceService.clear(ledgerId, username);
    }

    private String buildPlannerPrompt(Long ledgerId, String question,
                                      Optional<AnalysisContextSummary> priorContext,
                                      List<AnalysisConversationLine> recent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是账本分析计划器。\n");
        prompt.append(promptBuilder.buildPlannerRules());
        prompt.append(promptBuilder.buildOptionSnippet(ledgerId));
        priorContext.ifPresent(summary -> {
            prompt.append("最近成功上下文：\n");
            try {
                prompt.append(objectMapper.writeValueAsString(summary)).append('\n');
            } catch (JsonProcessingException e) {
                logger.warn("序列化分析上下文失败 ledgerId={}", ledgerId);
            }
        });
        if (!recent.isEmpty()) {
            prompt.append("最近对话：\n");
            for (int i = recent.size() - 1; i >= 0; i--) {
                AnalysisConversationLine line = recent.get(i);
                prompt.append(line.getRole()).append(": ").append(line.getContent()).append('\n');
            }
        }
        prompt.append("用户问题：\n").append(question);
        return prompt.toString();
    }

    private AnalysisRequestClass classifyRequest(RawAnalysisPlan raw) {
        if (raw == null || raw.getQueries() == null || raw.getQueries().isEmpty()
                || raw.getOperation() == null || raw.getOperation().isBlank()) {
            return AnalysisRequestClass.CLARIFICATION;
        }
        return AnalysisRequestClass.FORMAL;
    }

    private AnalysisChatResponse persistTerminal(Long ledgerId, String username, String question,
                                                 String answer, AnalysisResponseStatus status,
                                                 Instant started, int queryCount, int rowCount) {
        AnalysisChatResponse response = persistenceService.saveRound(
            ledgerId, username, question, answer, null, status, null);
        usageService.record(username, usageOutcome(status, started, queryCount, rowCount));
        logger.info("analysis chat status={} queries={} rows={}", status, queryCount, rowCount);
        return response;
    }

    private AnalysisUsageOutcome usageOutcome(AnalysisResponseStatus status, Instant started,
                                              int queryCount, int rowCount) {
        Duration duration = Duration.between(started, Instant.now());
        return switch (status) {
            case OK -> AnalysisUsageOutcome.success(duration, queryCount, rowCount);
            case NO_DATA -> AnalysisUsageOutcome.noData(duration, queryCount);
            case PARTIAL_RESULT -> AnalysisUsageOutcome.partial(duration, queryCount, rowCount);
            case CLARIFICATION_REQUIRED -> AnalysisUsageOutcome.clarification(duration);
            case INVALID_PLAN, AI_UNAVAILABLE, QUERY_FAILED -> AnalysisUsageOutcome.failed(status, duration);
            case RATE_LIMITED -> throw new BusinessException("今日提问次数已达上限，明日再来", "RATE_LIMITED");
        };
    }

    private boolean contextUpdateStatuses(AnalysisResponseStatus status) {
        return status == AnalysisResponseStatus.OK
            || status == AnalysisResponseStatus.NO_DATA
            || status == AnalysisResponseStatus.PARTIAL_RESULT;
    }

    private AnalysisContextSummary toContextSummary(NormalizedAnalysisPlan plan, AnalysisExecutionOutcome outcome) {
        NormalizedAnalysisQuery first = plan.queries().isEmpty() ? null : plan.queries().get(0);
        return new AnalysisContextSummary(
            first == null ? null : first.periodSemantic(),
            first == null ? null : first.period(),
            first == null ? null : first.transactionType(),
            first == null ? null : first.metric(),
            first == null ? null : first.dimension(),
            first == null ? null : first.filters(),
            outcome.results().isEmpty() ? null : outcome.results().get(0).kind(),
            Map.of("operation", plan.operation().name(),
                "title", plan.title() == null ? "" : plan.title()));
    }

    private int queryCount(NormalizedAnalysisPlan plan) {
        return plan == null || plan.queries() == null ? 0 : plan.queries().size();
    }

    private int resultRowCount(AnalysisExecutionOutcome outcome) {
        return outcome == null || outcome.results() == null ? 0 : outcome.results().size();
    }

    private String serializePayload(AnalysisPayloadV2 payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.error("序列化分析 payload 失败", e);
            return null;
        }
    }
}
