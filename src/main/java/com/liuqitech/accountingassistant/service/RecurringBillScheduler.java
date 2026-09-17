package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.GenerateRecurringBillsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringBillScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RecurringBillScheduler.class);

    private final RecurringBillService recurringBillService;

    public RecurringBillScheduler(RecurringBillService recurringBillService) {
        this.recurringBillService = recurringBillService;
    }

    // zone 显式钉在业务时区：公网服务器系统时区常为 UTC，"每日凌晨跑账单"须按中国时间触发
    @Scheduled(cron = "${app.recurring-bills.cron:0 5 0 * * *}", zone = "Asia/Shanghai")
    public void generateDueRecurringBills() {
        GenerateRecurringBillsResult result = recurringBillService.generateDueForAllLedgers();
        if (result.getProcessedRuleCount() > 0) {
            logger.info("周期账单自动生成完成: rules={}, generated={}, skipped={}",
                    result.getProcessedRuleCount(), result.getGeneratedCount(), result.getSkippedCount());
        }
    }
}
