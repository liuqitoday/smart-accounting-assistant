package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.CreateLedgerRequest;
import com.liuqitech.accountingassistant.dto.InviteMemberRequest;
import com.liuqitech.accountingassistant.dto.LedgerDto;
import com.liuqitech.accountingassistant.dto.LedgerMemberDto;
import com.liuqitech.accountingassistant.dto.SetActiveLedgerRequest;
import com.liuqitech.accountingassistant.dto.TransferOwnershipRequest;
import com.liuqitech.accountingassistant.dto.UpdateLedgerRequest;
import com.liuqitech.accountingassistant.dto.UpdateMemberRoleRequest;
import com.liuqitech.accountingassistant.interceptor.LedgerContext;
import com.liuqitech.accountingassistant.service.LedgerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 账本与成员管理控制器。
 *
 * <p>不挂 LedgerContextInterceptor：当前账本由路径 {id} 指定，权限由 LedgerService 内部校验。
 * 方法不吞异常，AccessDenied/ResourceNotFound/Business 异常交由 GlobalExceptionHandler 映射为 403/404/400。</p>
 */
@RestController
@RequestMapping("/api/ledgers")
public class LedgerController {

    private static final Logger logger = LoggerFactory.getLogger(LedgerController.class);

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    // ==================== 账本 ====================

    @PostMapping
    public ApiResponse<LedgerDto> createLedger(@Valid @RequestBody CreateLedgerRequest req,
                                               HttpServletRequest request) {
        String username = LedgerContext.username(request);
        return ApiResponse.success(ledgerService.createLedger(username, req), "账本创建成功");
    }

    @GetMapping
    public ApiResponse<List<LedgerDto>> listMyLedgers(HttpServletRequest request) {
        String username = LedgerContext.username(request);
        return ApiResponse.success(ledgerService.listMyLedgers(username));
    }

    @GetMapping("/{id}")
    public ApiResponse<LedgerDto> getLedger(@PathVariable Long id, HttpServletRequest request) {
        String username = LedgerContext.username(request);
        return ApiResponse.success(ledgerService.getLedger(username, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<LedgerDto> updateLedger(@PathVariable Long id,
                                               @Valid @RequestBody UpdateLedgerRequest req,
                                               HttpServletRequest request) {
        String username = LedgerContext.username(request);
        return ApiResponse.success(ledgerService.updateLedger(username, id, req), "账本更新成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteLedger(@PathVariable Long id, HttpServletRequest request) {
        String username = LedgerContext.username(request);
        ledgerService.deleteLedger(username, id);
        return ApiResponse.success(null, "账本已删除");
    }

    // ==================== 切换当前账本 ====================

    @PutMapping("/active")
    public ApiResponse<Void> setActiveLedger(@Valid @RequestBody SetActiveLedgerRequest req,
                                             HttpServletRequest request) {
        String username = LedgerContext.username(request);
        ledgerService.setActiveLedger(username, req.getLedgerId());
        return ApiResponse.success(null);
    }

    // ==================== 默认账本 ====================

    @PutMapping("/{id}/default")
    public ApiResponse<Void> setDefaultLedger(@PathVariable Long id, HttpServletRequest request) {
        String username = LedgerContext.username(request);
        ledgerService.setDefaultLedger(username, id);
        return ApiResponse.success(null, "默认账本已更新");
    }

    // ==================== 成员 ====================

    @GetMapping("/{id}/members")
    public ApiResponse<List<LedgerMemberDto>> listMembers(@PathVariable Long id, HttpServletRequest request) {
        String username = LedgerContext.username(request);
        return ApiResponse.success(ledgerService.listMembers(username, id));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<LedgerMemberDto> inviteMember(@PathVariable Long id,
                                                     @Valid @RequestBody InviteMemberRequest req,
                                                     HttpServletRequest request) {
        String username = LedgerContext.username(request);
        return ApiResponse.success(ledgerService.inviteMember(username, id, req), "已添加成员");
    }

    @PutMapping("/{id}/members/{memberUsername}")
    public ApiResponse<LedgerMemberDto> updateMemberRole(@PathVariable Long id,
                                                         @PathVariable String memberUsername,
                                                         @Valid @RequestBody UpdateMemberRoleRequest req,
                                                         HttpServletRequest request) {
        String username = LedgerContext.username(request);
        return ApiResponse.success(ledgerService.updateMemberRole(username, id, memberUsername, req.getRole()), "角色已更新");
    }

    @DeleteMapping("/{id}/members/{memberUsername}")
    public ApiResponse<Void> removeMember(@PathVariable Long id,
                                          @PathVariable String memberUsername,
                                          HttpServletRequest request) {
        String username = LedgerContext.username(request);
        ledgerService.removeMember(username, id, memberUsername);
        return ApiResponse.success(null, "已移除成员");
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<Void> leaveLedger(@PathVariable Long id, HttpServletRequest request) {
        String username = LedgerContext.username(request);
        ledgerService.leaveLedger(username, id);
        return ApiResponse.success(null, "已退出账本");
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<Void> transferOwnership(@PathVariable Long id,
                                               @Valid @RequestBody TransferOwnershipRequest req,
                                               HttpServletRequest request) {
        String username = LedgerContext.username(request);
        ledgerService.transferOwnership(username, id, req.getUsername());
        return ApiResponse.success(null, "所有权已转让");
    }
}
