package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.CreateRecurringBillRequest;
import com.liuqitech.accountingassistant.dto.GenerateRecurringBillsResult;
import com.liuqitech.accountingassistant.dto.RecurringBillDto;
import com.liuqitech.accountingassistant.dto.UpdateRecurringBillEnabledRequest;
import com.liuqitech.accountingassistant.dto.UpdateRecurringBillRequest;
import com.liuqitech.accountingassistant.interceptor.LedgerContext;
import com.liuqitech.accountingassistant.service.RecurringBillService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recurring-bills")
public class RecurringBillController {

    private static final Logger logger = LoggerFactory.getLogger(RecurringBillController.class);

    private final RecurringBillService recurringBillService;

    public RecurringBillController(RecurringBillService recurringBillService) {
        this.recurringBillService = recurringBillService;
    }

    @GetMapping
    public ApiResponse<List<RecurringBillDto>> list(HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        return ApiResponse.success(recurringBillService.list(ledgerId));
    }

    @PostMapping
    public ApiResponse<RecurringBillDto> create(@Valid @RequestBody CreateRecurringBillRequest req,
                                                HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        String username = LedgerContext.username(request);
        logger.info("账本 {} 创建周期账单规则: {}", ledgerId, req.getName());
        return ApiResponse.success(recurringBillService.create(ledgerId, username, req), "周期账单已创建");
    }

    @PutMapping("/{id}")
    public ApiResponse<RecurringBillDto> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateRecurringBillRequest req,
                                                HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 更新周期账单规则: {}", ledgerId, id);
        return ApiResponse.success(recurringBillService.update(ledgerId, id, req), "周期账单已更新");
    }

    @PutMapping("/{id}/enabled")
    public ApiResponse<RecurringBillDto> setEnabled(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateRecurringBillEnabledRequest req,
                                                    HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        return ApiResponse.success(recurringBillService.setEnabled(ledgerId, id, req.getEnabled()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        recurringBillService.delete(ledgerId, id);
        return ApiResponse.success(null, "周期账单已删除");
    }

    @PostMapping("/generate-due")
    public ApiResponse<GenerateRecurringBillsResult> generateDue(HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        return ApiResponse.success(recurringBillService.generateDueForLedger(ledgerId), "到期周期账单已处理");
    }

    @PostMapping("/{id}/generate-due")
    public ApiResponse<GenerateRecurringBillsResult> generateDueForBill(@PathVariable Long id,
                                                                        HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        return ApiResponse.success(recurringBillService.generateDueForBill(ledgerId, id), "到期周期账单已处理");
    }
}
