package com.liuqitech.accountingassistant.integration;

import com.liuqitech.accountingassistant.dto.CreateRecurringBillRequest;
import com.liuqitech.accountingassistant.dto.GenerateRecurringBillsResult;
import com.liuqitech.accountingassistant.dto.RecurringBillDto;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.enums.RecurringFrequency;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.LedgerRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.service.CategoryService;
import com.liuqitech.accountingassistant.service.RecurringBillService;
import com.liuqitech.accountingassistant.util.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:recurringtransferdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class RecurringTransferIntegrationTest {

    static final String USERNAME = "recurring_user";

    @Autowired RecurringBillService recurringBillService;
    @Autowired CategoryService categoryService;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired LedgerRepository ledgerRepository;
    @Autowired LedgerMemberRepository ledgerMemberRepository;
    @Autowired TransactionRepository transactionRepository;

    Long ledgerId;
    Category transferCategory;
    Category expenseCategory;
    Long accountA;
    Long accountB;

    @BeforeEach
    void setUp() {
        Ledger ledger = ledgerRepository.save(new Ledger("周期转账测试账本", USERNAME));
        ledgerId = ledger.getId();
        ledgerMemberRepository.save(new LedgerMember(ledgerId, USERNAME, LedgerRole.OWNER));
        transferCategory = category(CategoryService.TRANSFER_CATEGORY_NAME, TransactionType.TRANSFER);
        expenseCategory = category("测试支出", TransactionType.EXPENSE);
        accountA = account("工资卡").getId();
        accountB = account("房贷账户").getId();
    }

    @Test
    void createTransferRuleStoresCounterAccountAndSentinelCategory() {
        RecurringBillDto dto = recurringBillService.create(ledgerId, USERNAME, transferRequest(accountA, accountB));

        assertThat(dto.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(dto.getAccountId()).isEqualTo(accountA);
        assertThat(dto.getCounterAccountId()).isEqualTo(accountB);
        assertThat(dto.getCategoryId()).isEqualTo(transferCategory.getId());
    }

    @Test
    void createTransferRuleRejectsSameAccounts() {
        assertThatThrownBy(() -> recurringBillService.create(ledgerId, USERNAME, transferRequest(accountA, accountA)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("转出和转入账户不能相同");
    }

    @Test
    void createTransferRuleRejectsMissingCounterAccount() {
        assertThatThrownBy(() -> recurringBillService.create(ledgerId, USERNAME, transferRequest(accountA, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择转出和转入账户");
    }

    @Test
    void createExpenseRuleStillWorksAndHasNullCounterAccount() {
        CreateRecurringBillRequest req = baseRequest(TransactionType.EXPENSE);
        req.setCategoryId(expenseCategory.getId());
        req.setAccountId(accountA);

        RecurringBillDto dto = recurringBillService.create(ledgerId, USERNAME, req);

        assertThat(dto.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(dto.getCategoryId()).isEqualTo(expenseCategory.getId());
        assertThat(dto.getCounterAccountId()).isNull();
    }

    // ── helpers ──

    @Test
    void generateTransferRuleProducesTransferTransaction() {
        RecurringBillDto rule = recurringBillService.create(ledgerId, USERNAME, dueTransferRequest());

        GenerateRecurringBillsResult result = recurringBillService.generateDueForBill(ledgerId, rule.getId());

        assertThat(result.getGeneratedCount()).isEqualTo(1);
        List<Transaction> all = transactionRepository.findAll();
        assertThat(all).hasSize(1);
        Transaction txn = all.get(0);
        assertThat(txn.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(txn.getAccountId()).isEqualTo(accountA);
        assertThat(txn.getCounterAccountId()).isEqualTo(accountB);
        assertThat(txn.getRecurringBillId()).isEqualTo(rule.getId());
        assertThat(txn.getAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void generateTransferRuleIsIdempotent() {
        RecurringBillDto rule = recurringBillService.create(ledgerId, USERNAME, dueTransferRequest());

        recurringBillService.generateDueForBill(ledgerId, rule.getId());
        GenerateRecurringBillsResult second = recurringBillService.generateDueForBill(ledgerId, rule.getId());

        // 第二次 nextRunDate 已推进到下月（> today），不再生成，无重复行
        assertThat(second.getGeneratedCount()).isZero();
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void generateIsolatesRuleWhoseAccountWasDeleted() {
        // 规则 1：转入账户已被删除 → 生成失败但被隔离
        Long ghost = account("待删除账户").getId();
        RecurringBillDto broken = recurringBillService.create(ledgerId, USERNAME,
                dueTransferRequest(accountA, ghost));
        accountRepository.deleteById(ghost);
        // 规则 2：正常收入规则
        RecurringBillDto healthy = recurringBillService.create(ledgerId, USERNAME, dueIncomeRequest());

        GenerateRecurringBillsResult result = recurringBillService.generateDueForLedger(ledgerId);

        // 坏规则被跳过，健康规则照常生成
        List<Transaction> all = transactionRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getRecurringBillId()).isEqualTo(healthy.getId());
        assertThat(result.getGeneratedCount()).isEqualTo(1);
    }

    private CreateRecurringBillRequest dueTransferRequest() {
        return dueTransferRequest(accountA, accountB);
    }

    private CreateRecurringBillRequest dueTransferRequest(Long from, Long to) {
        CreateRecurringBillRequest req = transferRequest(from, to);
        req.setStartDate(AppClock.today());
        req.setDayOfMonth(AppClock.today().getDayOfMonth());
        return req;
    }

    private CreateRecurringBillRequest dueIncomeRequest() {
        Category incomeCategory = category("测试收入", TransactionType.INCOME);
        CreateRecurringBillRequest req = baseRequest(TransactionType.INCOME);
        req.setName("每月工资");
        req.setDescription("工资");
        req.setCategoryId(incomeCategory.getId());
        req.setAccountId(accountA);
        req.setStartDate(AppClock.today());
        req.setDayOfMonth(AppClock.today().getDayOfMonth());
        return req;
    }

    private CreateRecurringBillRequest transferRequest(Long from, Long to) {
        CreateRecurringBillRequest req = baseRequest(TransactionType.TRANSFER);
        req.setAccountId(from);
        req.setCounterAccountId(to);
        return req;
    }

    private CreateRecurringBillRequest baseRequest(TransactionType type) {
        CreateRecurringBillRequest req = new CreateRecurringBillRequest();
        req.setName("每月还房贷");
        req.setAmount(new BigDecimal("5000.00"));
        req.setType(type);
        req.setDescription("房贷还款");
        req.setFrequency(RecurringFrequency.MONTHLY);
        req.setDayOfMonth(10);
        req.setStartDate(LocalDate.of(2026, 7, 1));
        return req;
    }

    private Category category(String name, TransactionType type) {
        Category category = new Category();
        category.setName(name);
        category.setLevel(1);
        category.setType(type);
        return categoryRepository.save(category);
    }

    private Account account(String name) {
        Account account = new Account();
        account.setName(name);
        account.setType(AccountType.DEBIT_CARD);
        account.setInitialBalance(new BigDecimal("0"));
        account.setLedgerId(ledgerId);
        account.setCreatedBy(USERNAME);
        return accountRepository.save(account);
    }
}
