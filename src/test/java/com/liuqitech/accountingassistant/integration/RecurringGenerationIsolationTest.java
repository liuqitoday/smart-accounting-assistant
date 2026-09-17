package com.liuqitech.accountingassistant.integration;

import com.liuqitech.accountingassistant.dto.CreateRecurringBillRequest;
import com.liuqitech.accountingassistant.dto.GenerateRecurringBillsResult;
import com.liuqitech.accountingassistant.dto.RecurringBillDto;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.enums.RecurringFrequency;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.LedgerRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.service.CategoryService;
import com.liuqitech.accountingassistant.service.RecurringBillService;
import com.liuqitech.accountingassistant.util.AppClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 逐规则事务隔离的真实提交边界验证：不加类级 @Transactional，
 * generateDueForLedger 会真正提交，暴露"单坏规则回滚整批"缺陷。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:recurringisolationdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RecurringGenerationIsolationTest {

    static final String USERNAME = "isolation_user";

    @Autowired RecurringBillService recurringBillService;
    @Autowired CategoryService categoryService;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired LedgerRepository ledgerRepository;
    @Autowired LedgerMemberRepository ledgerMemberRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void brokenRuleDoesNotRollBackHealthyRuleAcrossCommit() {
        Ledger ledger = ledgerRepository.save(new Ledger("隔离测试账本", USERNAME));
        Long ledgerId = ledger.getId();
        ledgerMemberRepository.save(new LedgerMember(ledgerId, USERNAME, LedgerRole.OWNER));
        category(CategoryService.TRANSFER_CATEGORY_NAME, TransactionType.TRANSFER);
        Category incomeCategory = category("测试收入", TransactionType.INCOME);

        Long accountA = account(ledgerId, "工资卡").getId();
        Long accountB = account(ledgerId, "房贷账户").getId();

        // 坏规则：转账 A→B，随后删除转入账户 B，今天到期
        RecurringBillDto broken = recurringBillService.create(ledgerId, USERNAME,
                dueTransferRequest(accountA, accountB));
        accountRepository.deleteById(accountB);

        // 健康规则：收入，今天到期
        RecurringBillDto healthy = recurringBillService.create(ledgerId, USERNAME,
                dueIncomeRequest(accountA, incomeCategory.getId()));

        // 真实提交边界：不应抛异常给调用方（含 commit 期 UnexpectedRollbackException）
        GenerateRecurringBillsResult[] holder = new GenerateRecurringBillsResult[1];
        assertThatCode(() -> holder[0] = recurringBillService.generateDueForLedger(ledgerId))
                .doesNotThrowAnyException();
        GenerateRecurringBillsResult result = holder[0];
        assertThat(result.getGeneratedCount()).isEqualTo(1);

        // 健康规则的交易在提交后仍存在，坏规则未产生任何行
        List<Transaction> all = transactionRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getRecurringBillId()).isEqualTo(healthy.getId());
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(broken.getId()).isNotNull();
    }

    private CreateRecurringBillRequest dueTransferRequest(Long from, Long to) {
        CreateRecurringBillRequest req = new CreateRecurringBillRequest();
        req.setName("每月还房贷");
        req.setAmount(new BigDecimal("5000.00"));
        req.setType(TransactionType.TRANSFER);
        req.setDescription("房贷还款");
        req.setFrequency(RecurringFrequency.MONTHLY);
        req.setDayOfMonth(AppClock.today().getDayOfMonth());
        req.setStartDate(AppClock.today());
        req.setAccountId(from);
        req.setCounterAccountId(to);
        return req;
    }

    private CreateRecurringBillRequest dueIncomeRequest(Long accountId, Long categoryId) {
        CreateRecurringBillRequest req = new CreateRecurringBillRequest();
        req.setName("每月工资");
        req.setAmount(new BigDecimal("5000.00"));
        req.setType(TransactionType.INCOME);
        req.setDescription("工资");
        req.setFrequency(RecurringFrequency.MONTHLY);
        req.setDayOfMonth(AppClock.today().getDayOfMonth());
        req.setStartDate(AppClock.today());
        req.setCategoryId(categoryId);
        req.setAccountId(accountId);
        return req;
    }

    private Category category(String name, TransactionType type) {
        Category category = new Category();
        category.setName(name);
        category.setLevel(1);
        category.setType(type);
        return categoryRepository.save(category);
    }

    private Account account(Long ledgerId, String name) {
        Account account = new Account();
        account.setName(name);
        account.setType(AccountType.DEBIT_CARD);
        account.setInitialBalance(new BigDecimal("0"));
        account.setLedgerId(ledgerId);
        account.setCreatedBy(USERNAME);
        return accountRepository.save(account);
    }
}
