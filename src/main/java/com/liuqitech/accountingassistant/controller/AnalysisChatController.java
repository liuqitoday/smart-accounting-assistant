package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.AnalysisChatRequest;
import com.liuqitech.accountingassistant.dto.AnalysisChatResponse;
import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.AnalysisMessageDto;
import com.liuqitech.accountingassistant.dto.PageResponseDto;
import com.liuqitech.accountingassistant.interceptor.LedgerContext;
import com.liuqitech.accountingassistant.service.AnalysisChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisChatController {

    private final AnalysisChatService analysisChatService;

    public AnalysisChatController(AnalysisChatService analysisChatService) {
        this.analysisChatService = analysisChatService;
    }

    /**
     * 提问
     */
    @PostMapping("/chat")
    public ApiResponse<AnalysisChatResponse> chat(@Valid @RequestBody AnalysisChatRequest chatRequest,
                                                  HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        String username = LedgerContext.username(request);

        AnalysisChatResponse response = analysisChatService.chat(ledgerId, username, chatRequest.getQuestion().trim());
        return ApiResponse.success(response);
    }

    /**
     * 拉取历史消息（倒序分页）
     */
    @GetMapping("/messages")
    public ApiResponse<PageResponseDto<AnalysisMessageDto>> getMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        String username = LedgerContext.username(request);

        PageResponseDto<AnalysisMessageDto> messages = analysisChatService.getMessages(ledgerId, username, page, size);
        return ApiResponse.success(messages);
    }

    /**
     * 清空当前账本+用户的聊天流
     */
    @DeleteMapping("/messages")
    public ApiResponse<Void> clearMessages(HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        String username = LedgerContext.username(request);

        analysisChatService.clearMessages(ledgerId, username);
        return ApiResponse.success(null, "对话已清空");
    }
}
