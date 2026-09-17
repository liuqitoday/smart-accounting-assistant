package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.GenerateRecurringBillsResult;
import com.liuqitech.accountingassistant.entity.RecurringBill;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.RecurringBillRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 单条周期账单规则的到期生成，运行在独立物理事务中。
 * 从 {@link RecurringBillService} 拆出，使一条坏规则回滚只污染自身事务，
 * 而不会经参与式事务把整批（含健康规则）标记 rollback-only 导致整批回滚。
 */
@Component
public class RecurringBillOccurrenceGenerator {

    private static final int MAX_OCCURRENCES_PER_RULE = 24;

    private final RecurringBillRepository recurringBillRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final TransferService transferService;

    public RecurringBillOccurrenceGenerator(RecurringBillRepository recurringBillRepository,
                                            TransactionRepository transactionRepository,
                                            TransactionService transactionService,
                                            TransferService transferService) {
        this.recurringBillRepository = recurringBillRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.transferService = transferService;
    }

    @Transactional
    public GenerateRecurringBillsResult generateForRule(RecurringBill detachedBill, LocalDate today) {
        GenerateRecurringBillsResult result = new GenerateRecurringBillsResult();
        result.addProcessedRule();

        // 上游 finder 事务已关闭，传入实体为游离态；在本事务内按账本 + 未删过滤重新加载为受管实体，
        // 以便惰性集合（tags）可加载、save 生效。用带 DeletedAtIsNull 的查询，
        // 闭合"finder 查询到重载之间规则被软删除仍生成一期"的窗口；查不到则抛异常，由上游按规则隔离跳过。
        RecurringBill bill = recurringBillRepository
                .findByIdAndLedgerIdAndDeletedAtIsNull(detachedBill.getId(), detachedBill.getLedgerId())
                .orElseThrow(() -> new IllegalStateException("周期账单规则不存在或已删除: " + detachedBill.getId()));

        int generatedOrSkipped = 0;
        while (bill.isEnabled()
                && bill.getNextRunDate() != null
                && !bill.getNextRunDate().isAfter(today)
                && generatedOrSkipped < MAX_OCCURRENCES_PER_RULE) {
            LocalDate occurrenceDate = bill.getNextRunDate();
            if (bill.getEndDate() != null && occurrenceDate.isAfter(bill.getEndDate())) {
                bill.setEnabled(false);
                break;
            }

            if (transactionRepository.existsByRecurringBillIdAndRecurringOccurrenceDate(bill.getId(), occurrenceDate)) {
                result.addSkipped();
            } else {
                Transaction transaction = createOccurrenceTransaction(bill, occurrenceDate);
                result.addGenerated(transaction.getId());
            }

            bill.setNextRunDate(RecurringBillScheduleCalculator.nextRunAfter(
                    bill.getFrequency(), occurrenceDate, bill.getDayOfMonth()));
            generatedOrSkipped++;
        }

        if (bill.isEnabled()
                && bill.getNextRunDate() != null
                && !bill.getNextRunDate().isAfter(today)
                && generatedOrSkipped >= MAX_OCCURRENCES_PER_RULE) {
            result.addTruncatedRule();
        }

        if (bill.getEndDate() != null
                && bill.getNextRunDate() != null
                && bill.getNextRunDate().isAfter(bill.getEndDate())) {
            bill.setEnabled(false);
        }
        recurringBillRepository.save(bill);
        return result;
    }

    private Transaction createOccurrenceTransaction(RecurringBill bill, LocalDate occurrenceDate) {
        if (bill.getType() == TransactionType.TRANSFER) {
            return transferService.createRecurringTransferOccurrence(bill, occurrenceDate);
        }
        TransactionCreationCommand command = new TransactionCreationCommand();
        command.setLedgerId(bill.getLedgerId());
        command.setUsername(bill.getCreatedBy());
        command.setAmount(bill.getAmount());
        command.setType(bill.getType());
        command.setDescription(bill.getDescription());
        command.setOriginalText(bill.getDescription());
        command.setCategoryId(bill.getCategoryId());
        command.setTransactionDate(occurrenceDate);
        command.setParsedMerchant(bill.getParsedMerchant());
        command.setRelatedUser(bill.getRelatedUser());
        command.setNote(bill.getNote());
        command.setAccountId(bill.getAccountId());
        command.setRecurringBillId(bill.getId());
        command.setRecurringOccurrenceDate(occurrenceDate);
        command.setTags(bill.getTags());
        return transactionService.createTransaction(command);
    }
}
