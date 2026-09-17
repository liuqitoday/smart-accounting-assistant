package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.CategoryStatisticsDto;
import com.liuqitech.accountingassistant.dto.StatisticsSummaryDto;
import com.liuqitech.accountingassistant.dto.TransactionDto;
import com.liuqitech.accountingassistant.dto.TrendDataDto;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.interceptor.LedgerContext;
import com.liuqitech.accountingassistant.service.StatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计控制器（按当前账本统计）
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsController.class);

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/summary")
    public ApiResponse<StatisticsSummaryDto> getSummary(
            @RequestParam(defaultValue = "current_month") String period,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("获取账本 {} 的统计汇总，时间段: {}", ledgerId, period);
        return ApiResponse.success(statisticsService.getSummary(ledgerId, period));
    }

    @GetMapping("/by-category")
    public ApiResponse<List<CategoryStatisticsDto>> getByCategory(
            @RequestParam(defaultValue = "current_month") String period,
            @RequestParam(defaultValue = "EXPENSE") TransactionType type,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("获取账本 {} 的分类统计，时间段: {}, 类型: {}", ledgerId, period, type);
        return ApiResponse.success(statisticsService.getByCategory(ledgerId, period, type));
    }

    @GetMapping("/trend")
    public ApiResponse<List<TrendDataDto>> getTrend(
            @RequestParam(defaultValue = "6") int months,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("获取账本 {} 的月度趋势，最近 {} 个月", ledgerId, months);
        return ApiResponse.success(statisticsService.getMonthlyTrend(ledgerId, months));
    }

    @GetMapping("/trend/daily")
    public ApiResponse<List<TrendDataDto>> getDailyTrend(
            @RequestParam(defaultValue = "current_month") String period,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("获取账本 {} 的日趋势，时间段: {}", ledgerId, period);
        return ApiResponse.success(statisticsService.getDailyTrend(ledgerId, period));
    }

    @GetMapping("/top-expenses")
    public ApiResponse<List<TransactionDto>> getTopExpenses(
            @RequestParam(defaultValue = "current_month") String period,
            HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("获取账本 {} 的大额支出排行，时间段: {}", ledgerId, period);
        return ApiResponse.success(statisticsService.getTopExpenses(ledgerId, period));
    }

    @GetMapping("/recent")
    public ApiResponse<List<TransactionDto>> getRecentTransactions(HttpServletRequest request) {

        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("获取账本 {} 的最近交易", ledgerId);
        return ApiResponse.success(statisticsService.getRecentTransactions(ledgerId));
    }
}
