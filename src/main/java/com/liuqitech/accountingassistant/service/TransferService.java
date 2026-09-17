package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.CreateTransferRequest;
import com.liuqitech.accountingassistant.dto.TransactionDto;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.RecurringBill;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.mapper.TransactionMapper;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.util.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;

/**
 * 账户间转账服务。
 *
 * <p>转账复用 transactions 表，以 type=TRANSFER 的单行存储：account_id 为转出方，
 * counter_account_id 为转入方。余额按动态聚合计算（见 {@link AccountService}），
 * 不修改账户期初余额。转账不计入收支统计。</p>
 */
@Service
@Transactional
public class TransferService {

    private static final Logger logger = LoggerFactory.getLogger(TransferService.class);

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final TransactionMapper transactionMapper;

    public TransferService(TransactionRepository transactionRepository,
                           AccountService accountService,
                           CategoryService categoryService,
                           TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.transactionMapper = transactionMapper;
    }

    /**
     * 创建一笔转账。校验：两账户存在且同账本、互不相同、金额 > 0；不校验余额（允许透支）。
     */
    public TransactionDto createTransfer(Long ledgerId, String username, CreateTransferRequest request) {
        if (request.getFromAccountId() == null || request.getToAccountId() == null) {
            throw new BusinessException("请选择转出和转入账户");
        }
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new BusinessException("转出和转入账户不能相同");
        }
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("转账金额必须大于 0");
        }

        Account from = accountService.requireAccountInLedger(ledgerId, request.getFromAccountId());
        Account to = accountService.requireAccountInLedger(ledgerId, request.getToAccountId());

        Transaction transaction = buildTransfer(ledgerId, username, from, to, amount,
                request.getTransferDate() != null ? request.getTransferDate() : AppClock.today(),
                null, request.getNote());

        Transaction saved = transactionRepository.save(transaction);
        logger.info("账本 {} 创建转账: {} ({} -> {}, {})",
                ledgerId, saved.getId(), from.getName(), to.getName(), amount);
        return transactionMapper.toDto(saved);
    }

    /**
     * 组装一笔转账交易（未持久化）。description 为空则用 "转出 → 转入 转账" 标签。
     */
    private Transaction buildTransfer(Long ledgerId, String username, Account from, Account to,
                                      BigDecimal amount, LocalDate date, String description, String note) {
        Category transferCategory = categoryService.getTransferCategory();
        String label = from.getName() + " → " + to.getName() + " 转账";
        String effectiveDescription = (description != null && !description.isBlank()) ? description.trim() : label;

        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.TRANSFER);
        transaction.setAmount(amount);
        transaction.setAccountId(from.getId());
        transaction.setCounterAccountId(to.getId());
        transaction.setTransactionDate(date);
        transaction.setCategoryEntity(transferCategory);
        transaction.setDescription(effectiveDescription);
        transaction.setOriginalText(effectiveDescription);
        transaction.setNote(note);
        transaction.setLedgerId(ledgerId);
        transaction.setCreatedBy(username);
        return transaction;
    }

    /**
     * 为周期账单规则生成一期转账交易（带幂等元数据与标签）。账户已被删除时抛 ResourceNotFoundException。
     */
    public Transaction createRecurringTransferOccurrence(RecurringBill bill, LocalDate occurrenceDate) {
        Account from = accountService.requireAccountInLedger(bill.getLedgerId(), bill.getAccountId());
        Account to = accountService.requireAccountInLedger(bill.getLedgerId(), bill.getCounterAccountId());

        Transaction transaction = buildTransfer(bill.getLedgerId(), bill.getCreatedBy(), from, to,
                bill.getAmount(), occurrenceDate, bill.getDescription(), bill.getNote());
        transaction.setRecurringBillId(bill.getId());
        transaction.setRecurringOccurrenceDate(occurrenceDate);
        // 复制标签集合，避免与规则实体共享同一 PersistentSet（Hibernate 禁止共享引用）
        transaction.setTags(bill.getTags() == null ? new HashSet<>() : new HashSet<>(bill.getTags()));

        Transaction saved = transactionRepository.save(transaction);
        logger.info("账本 {} 生成周期转账: 规则 {} 于 {} ({} -> {}, {})",
                bill.getLedgerId(), bill.getId(), occurrenceDate, from.getName(), to.getName(), bill.getAmount());
        return saved;
    }
}
