package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.ImportResult;
import com.liuqitech.accountingassistant.dto.PageResponseDto;
import com.liuqitech.accountingassistant.dto.SaveTransactionRequest;
import com.liuqitech.accountingassistant.dto.TransactionDto;
import com.liuqitech.accountingassistant.dto.TransactionParseRequest;
import com.liuqitech.accountingassistant.dto.TransactionParseResponse;
import com.liuqitech.accountingassistant.dto.UpdateTransactionRequest;
import com.liuqitech.accountingassistant.dto.UpdateTagsRequest;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.interceptor.LedgerContext;
import com.liuqitech.accountingassistant.service.AiRateLimitService;
import com.liuqitech.accountingassistant.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 交易记录控制器（数据归属当前账本，由 X-Ledger-Id 指定）。
 * 错误统一抛异常，由 GlobalExceptionHandler 映射为 HTTP 状态 + ApiResponse 错误体。
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;
    private final AiRateLimitService aiRateLimitService;

    public TransactionController(TransactionService transactionService,
                                 AiRateLimitService aiRateLimitService) {
        this.transactionService = transactionService;
        this.aiRateLimitService = aiRateLimitService;
    }

    /**
     * 解析交易类型字符串
     */
    private TransactionType parseTransactionType(String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        try {
            return TransactionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 用 BusinessException 携带友好提示（全局 IAE 处理器出于安全考虑不再回显原始 message）
            throw new BusinessException("无效的交易类型: " + type + "，请使用 INCOME、EXPENSE 或 TRANSFER", "ILLEGAL_ARGUMENT");
        }
    }

    /**
     * 仅解析交易文本（不保存）
     */
    @PostMapping("/parse-only")
    public ApiResponse<TransactionParseResponse> parseTransactionOnly(
            @Valid @RequestBody TransactionParseRequest request,
            HttpServletRequest httpRequest) {

        logger.info("收到交易解析请求（仅解析）: {}", request);

        aiRateLimitService.checkAndConsume(LedgerContext.username(httpRequest));
        Long ledgerId = LedgerContext.ledgerId(httpRequest);
        TransactionParseResponse response = transactionService.parseTransactionOnly(request, ledgerId);
        return ApiResponse.success(response, "交易解析成功");
    }

    /**
     * 保存已解析的交易记录
     */
    @PostMapping("/save")
    public ApiResponse<TransactionParseResponse> saveTransaction(
            @Valid @RequestBody SaveTransactionRequest saveRequest,
            HttpServletRequest request) {

        logger.info("保存交易记录: {}", saveRequest);

        Long ledgerId = LedgerContext.ledgerId(request);
        String username = LedgerContext.username(request);
        TransactionParseResponse response = transactionService.saveTransaction(saveRequest, ledgerId, username);
        return ApiResponse.success(response, "交易记录保存成功");
    }

    /**
     * 解析交易文本并创建记录
     */
    @PostMapping("/parse")
    public ApiResponse<TransactionParseResponse> parseTransaction(
            @Valid @RequestBody TransactionParseRequest request,
            HttpServletRequest httpRequest) {

        logger.info("收到交易解析请求: {}", request);

        aiRateLimitService.checkAndConsume(LedgerContext.username(httpRequest));
        Long ledgerId = LedgerContext.ledgerId(httpRequest);
        String username = LedgerContext.username(httpRequest);
        TransactionParseResponse response = transactionService.parseAndCreateTransaction(request, ledgerId, username);
        return ApiResponse.success(response, "交易解析成功");
    }

    /**
     * 获取交易记录列表（支持多条件筛选，包括关键词搜索）
     */
    @GetMapping
    public ApiResponse<PageResponseDto<TransactionDto>> getTransactions(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("获取账本 {} 的交易记录列表: keyword={}, type={}, startDate={}, endDate={}, tagIds={}, createdBy={}, categoryId={}, accountId={}, merchant={}, minAmount={}, maxAmount={}",
                ledgerId, keyword, type, startDate, endDate, tagIds, createdBy, categoryId, accountId, merchant, minAmount, maxAmount);

        TransactionType transactionType = parseTransactionType(type);
        PageResponseDto<TransactionDto> transactions = transactionService.queryTransactions(
                ledgerId, keyword, transactionType, startDate, endDate, tagIds, createdBy, categoryId,
                accountId, merchant, minAmount, maxAmount, pageable);

        return ApiResponse.success(transactions);
    }

    /**
     * 根据ID获取交易记录（限定当前账本）
     */
    @GetMapping("/{id}")
    public ApiResponse<TransactionDto> getTransactionById(@PathVariable Long id,
                                                          HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("根据ID获取交易记录: {} (ledger={})", id, ledgerId);

        TransactionDto transaction = transactionService.getTransactionById(id, ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException("交易记录不存在: " + id));
        return ApiResponse.success(transaction);
    }

    /**
     * 更新交易记录
     */
    @PutMapping("/{id}")
    public ApiResponse<TransactionDto> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionRequest updateRequest,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 更新交易记录: {} -> {}", ledgerId, id, updateRequest);

        TransactionDto updatedTransaction = transactionService.updateTransaction(id, updateRequest, ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException("交易记录不存在: " + id));
        return ApiResponse.success(updatedTransaction, "交易记录更新成功");
    }

    /**
     * 删除交易记录（软删除）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTransaction(
            @PathVariable Long id,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 删除交易记录: {}", ledgerId, id);

        if (!transactionService.deleteTransaction(id, ledgerId)) {
            throw new ResourceNotFoundException("交易记录不存在或已删除: " + id);
        }
        return ApiResponse.success(null, "交易记录删除成功");
    }

    /**
     * 恢复已删除的交易记录（撤销删除）
     */
    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restoreTransaction(
            @PathVariable Long id,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 恢复交易记录: {}", ledgerId, id);

        if (!transactionService.restoreTransaction(id, ledgerId)) {
            throw new ResourceNotFoundException("交易记录不存在或未被删除: " + id);
        }
        return ApiResponse.success(null, "交易记录恢复成功");
    }

    /**
     * 导出交易记录为CSV（支持筛选条件）。
     * 文件下载需要自定义 Content-Type/Content-Disposition，是唯一保留 ResponseEntity 的场景。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 导出交易记录，筛选条件 - 类型: {}, 日期范围: {} - {}, 标签: {}, 创建人: {}, 分类: {}, 账户: {}, 商家: {}, 金额: {} - {}",
                ledgerId, type, startDate, endDate, tagIds, createdBy, categoryId, accountId, merchant, minAmount, maxAmount);

        TransactionType transactionType = parseTransactionType(type);
        String csv = transactionService.exportTransactionsToCSV(
                ledgerId, transactionType, startDate, endDate, tagIds, createdBy, categoryId,
                accountId, merchant, minAmount, maxAmount);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "transactions.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(("﻿" + csv).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 更新交易的标签
     */
    @PostMapping("/{id}/tags")
    public ApiResponse<Void> updateTransactionTags(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateTagsRequest req,
                                                   HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 更新交易 {} 的标签", ledgerId, id);

        transactionService.updateTransactionTags(ledgerId, id, req.getTagIds());
        return ApiResponse.success(null);
    }

    /**
     * 导入交易记录CSV
     */
    @PostMapping("/import")
    public ApiResponse<ImportResult> importTransactions(@RequestParam("file") MultipartFile file,
                                                        HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        String username = LedgerContext.username(request);
        logger.info("用户 {} 向账本 {} 上传 CSV 导入", username, ledgerId);

        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new BusinessException("只支持 CSV 文件");
        }

        ImportResult result = transactionService.importTransactionsFromCSV(ledgerId, username, file);
        return ApiResponse.success(result);
    }
}
