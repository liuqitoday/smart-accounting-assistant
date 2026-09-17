package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.AccountDto;
import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.CreateAccountRequest;
import com.liuqitech.accountingassistant.dto.UpdateAccountRequest;
import com.liuqitech.accountingassistant.interceptor.LedgerContext;
import com.liuqitech.accountingassistant.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 账户管理控制器（账户归属当前账本，成员共享；由 X-Ledger-Id 指定账本）。
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 获取当前账本的所有账户（含当前余额）
     */
    @GetMapping
    public ApiResponse<List<AccountDto>> getAccounts(HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("获取账本 {} 的账户列表", ledgerId);
        return ApiResponse.success(accountService.getLedgerAccounts(ledgerId));
    }

    /**
     * 创建账户
     */
    @PostMapping
    public ApiResponse<AccountDto> createAccount(@Valid @RequestBody CreateAccountRequest req,
                                                 HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        String username = LedgerContext.username(request);
        logger.info("账本 {} 创建账户: {}", ledgerId, req.getName());
        return ApiResponse.success(accountService.createAccount(ledgerId, username, req));
    }

    /**
     * 更新账户
     */
    @PutMapping("/{id}")
    public ApiResponse<AccountDto> updateAccount(@PathVariable Long id,
                                                 @Valid @RequestBody UpdateAccountRequest req,
                                                 HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 更新账户 {}", ledgerId, id);
        return ApiResponse.success(accountService.updateAccount(ledgerId, id, req));
    }

    /**
     * 删除账户
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAccount(@PathVariable Long id, HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 删除账户 {}", ledgerId, id);
        accountService.deleteAccount(ledgerId, id);
        return ApiResponse.success(null);
    }
}
