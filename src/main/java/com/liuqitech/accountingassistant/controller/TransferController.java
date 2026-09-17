package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.CreateTransferRequest;
import com.liuqitech.accountingassistant.dto.TransactionDto;
import com.liuqitech.accountingassistant.interceptor.LedgerContext;
import com.liuqitech.accountingassistant.service.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 账户间转账控制器（数据归属当前账本，由 X-Ledger-Id 指定）。
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private static final Logger logger = LoggerFactory.getLogger(TransferController.class);

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * 创建一笔账户间转账
     */
    @PostMapping
    public ApiResponse<TransactionDto> createTransfer(@Valid @RequestBody CreateTransferRequest request,
                                                      HttpServletRequest httpRequest) {
        Long ledgerId = LedgerContext.ledgerId(httpRequest);
        String username = LedgerContext.username(httpRequest);
        logger.info("账本 {} 创建转账请求: {} -> {}", ledgerId, request.getFromAccountId(), request.getToAccountId());
        TransactionDto saved = transferService.createTransfer(ledgerId, username, request);
        return ApiResponse.success(saved, "转账成功");
    }
}
