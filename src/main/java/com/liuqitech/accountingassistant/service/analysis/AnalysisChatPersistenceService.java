package com.liuqitech.accountingassistant.service.analysis;

import com.liuqitech.accountingassistant.dto.AnalysisChatResponse;
import com.liuqitech.accountingassistant.dto.AnalysisMessageDto;
import com.liuqitech.accountingassistant.dto.PageResponseDto;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisContextSummary;
import com.liuqitech.accountingassistant.entity.AnalysisChatMessage;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.AnalysisChatContextRepository;
import com.liuqitech.accountingassistant.repository.AnalysisChatMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class AnalysisChatPersistenceService {

    private static final Set<AnalysisResponseStatus> CONTEXT_UPDATE_STATUSES = EnumSet.of(
        AnalysisResponseStatus.OK,
        AnalysisResponseStatus.NO_DATA,
        AnalysisResponseStatus.PARTIAL_RESULT);
    private static final Set<AnalysisResponseStatus> FAILED_DB_STATUSES = EnumSet.of(
        AnalysisResponseStatus.AI_UNAVAILABLE,
        AnalysisResponseStatus.QUERY_FAILED);

    private final AnalysisChatMessageRepository messageRepository;
    private final AnalysisChatContextRepository contextRepository;
    private final AnalysisContextService contextService;

    public AnalysisChatPersistenceService(AnalysisChatMessageRepository messageRepository,
                                          AnalysisChatContextRepository contextRepository,
                                          AnalysisContextService contextService) {
        this.messageRepository = messageRepository;
        this.contextRepository = contextRepository;
        this.contextService = contextService;
    }

    @Transactional
    public AnalysisChatResponse saveRound(Long ledgerId, String userId, String question,
                                          String answer, String payload,
                                          AnalysisResponseStatus status,
                                          AnalysisContextSummary successContext) {
        if (status == AnalysisResponseStatus.RATE_LIMITED) {
            throw new BusinessException("今日提问次数已达上限，明日再来", "RATE_LIMITED");
        }

        String dbStatus = FAILED_DB_STATUSES.contains(status) ? "FAILED" : "OK";
        AnalysisChatMessage userMsg = messageRepository.save(
            new AnalysisChatMessage(ledgerId, userId, "USER", question, null, dbStatus));
        AnalysisChatMessage assistantMsg = messageRepository.save(
            new AnalysisChatMessage(ledgerId, userId, "ASSISTANT", answer, payload, dbStatus));

        if (CONTEXT_UPDATE_STATUSES.contains(status) && successContext != null) {
            contextService.upsertSuccess(ledgerId, userId, successContext);
        }

        return new AnalysisChatResponse(toDto(userMsg), toDto(assistantMsg));
    }

    public PageResponseDto<AnalysisMessageDto> getMessages(Long ledgerId, String userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<AnalysisChatMessage> messagePage = messageRepository.findByLedgerIdAndUserId(
            ledgerId, userId, PageRequest.of(safePage, safeSize));
        return PageResponseDto.from(messagePage, messagePage.getContent().stream().map(this::toDto).toList());
    }

    @Transactional
    public void clear(Long ledgerId, String userId) {
        messageRepository.deleteByLedgerIdAndUserId(ledgerId, userId);
        contextRepository.deleteByLedgerIdAndUserId(ledgerId, userId);
    }

    private AnalysisMessageDto toDto(AnalysisChatMessage msg) {
        return new AnalysisMessageDto(
            msg.getId(),
            msg.getRole(),
            msg.getContent(),
            msg.getPayload(),
            msg.getStatus(),
            msg.getCreatedAt());
    }
}
