package com.liuqitech.accountingassistant.repository.specification;

import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TransactionSpecifications 测试类
 * 验证按一级类目和二级类目筛选的功能
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionSpecificationsTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Long ledgerId;
    private Category parentCategory;
    private Category childCategory1;
    private Category childCategory2;

    @BeforeEach
    void setUp() {
        ledgerId = 1L;

        // 创建测试分类：一级类目「餐饮」，二级类目「早餐」和「午餐」
        parentCategory = new Category();
        parentCategory.setName("餐饮");
        parentCategory.setLevel(1);
        parentCategory.setType(TransactionType.EXPENSE);
        parentCategory = categoryRepository.save(parentCategory);

        childCategory1 = new Category();
        childCategory1.setName("早餐");
        childCategory1.setLevel(2);
        childCategory1.setType(TransactionType.EXPENSE);
        childCategory1.setParent(parentCategory);
        childCategory1 = categoryRepository.save(childCategory1);

        childCategory2 = new Category();
        childCategory2.setName("午餐");
        childCategory2.setLevel(2);
        childCategory2.setType(TransactionType.EXPENSE);
        childCategory2.setParent(parentCategory);
        childCategory2 = categoryRepository.save(childCategory2);

        // 创建测试交易记录
        Transaction tx1 = new Transaction();
        tx1.setLedgerId(ledgerId);
        tx1.setAmount(new BigDecimal("15.00"));
        tx1.setType(TransactionType.EXPENSE);
        tx1.setDescription("早餐包子");
        tx1.setOriginalText("早餐包子 15元");
        tx1.setTransactionDate(LocalDate.now());
        tx1.setCategoryEntity(childCategory1);
        transactionRepository.save(tx1);

        Transaction tx2 = new Transaction();
        tx2.setLedgerId(ledgerId);
        tx2.setAmount(new BigDecimal("30.00"));
        tx2.setType(TransactionType.EXPENSE);
        tx2.setDescription("午餐盒饭");
        tx2.setOriginalText("午餐盒饭 30元");
        tx2.setTransactionDate(LocalDate.now());
        tx2.setCategoryEntity(childCategory2);
        transactionRepository.save(tx2);
    }

    @Test
    void testFilterByParentCategory() {
        // 测试按一级类目「餐饮」筛选，应该返回所有子类目的交易
        Specification<Transaction> spec = TransactionSpecifications.buildQuery(
                ledgerId, null, null, null, null, null, null, parentCategory.getId(),
                null, null, null, null
        );

        List<Transaction> results = transactionRepository.findAll(spec);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Transaction::getDescription)
                .containsExactlyInAnyOrder("早餐包子", "午餐盒饭");
    }

    @Test
    void testFilterByChildCategory() {
        // 测试按二级类目「早餐」筛选，应该只返回早餐的交易
        Specification<Transaction> spec = TransactionSpecifications.buildQuery(
                ledgerId, null, null, null, null, null, null, childCategory1.getId(),
                null, null, null, null
        );

        List<Transaction> results = transactionRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDescription()).isEqualTo("早餐包子");
        assertThat(results.get(0).getCategoryId()).isEqualTo(childCategory1.getId());
    }

    @Test
    void testFilterByAnotherChildCategory() {
        // 测试按二级类目「午餐」筛选，应该只返回午餐的交易
        Specification<Transaction> spec = TransactionSpecifications.buildQuery(
                ledgerId, null, null, null, null, null, null, childCategory2.getId(),
                null, null, null, null
        );

        List<Transaction> results = transactionRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDescription()).isEqualTo("午餐盒饭");
        assertThat(results.get(0).getCategoryId()).isEqualTo(childCategory2.getId());
    }

    @Test
    void testNoCategoryFilter() {
        // 测试不指定分类筛选，应该返回所有交易
        Specification<Transaction> spec = TransactionSpecifications.buildQuery(
                ledgerId, null, null, null, null, null, null, null,
                null, null, null, null
        );

        List<Transaction> results = transactionRepository.findAll(spec);

        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void filterByAccountMerchantAndAmountRangeReturnsOnlyMatchingTransaction() {
        Account coffeeAccount = account("支付宝");
        Account otherAccount = account("银行卡");

        Transaction coffee = transaction("咖啡拿铁", "咖啡", new BigDecimal("68.00"), coffeeAccount.getId());
        transaction("午餐盒饭", "食堂", new BigDecimal("30.00"), otherAccount.getId());
        transaction("高价咖啡", "咖啡", new BigDecimal("128.00"), coffeeAccount.getId());
        transaction("便宜咖啡", "咖啡", new BigDecimal("18.00"), coffeeAccount.getId());

        Specification<Transaction> spec = TransactionSpecifications.buildQuery(
                ledgerId, null, null, null, null, null, null, null,
                coffeeAccount.getId(), "咖啡", new BigDecimal("50"), new BigDecimal("100")
        );

        List<Transaction> results = transactionRepository.findAll(spec);

        assertThat(results).extracting(Transaction::getId).containsExactly(coffee.getId());
        assertThat(results.get(0).getParsedMerchant()).isEqualTo("咖啡");
        assertThat(results.get(0).getAccountId()).isEqualTo(coffeeAccount.getId());
        assertThat(otherAccount.getId()).isNotEqualTo(coffeeAccount.getId());
    }

    @Test
    void merchantAndKeywordWildcardsAreMatchedLiterally() {
        Account account = account("支付宝");
        transaction("星巴克咖啡", "星巴克", new BigDecimal("3.00"), account.getId());
        Transaction literal = transaction("含百分号%的支出", "星%克", new BigDecimal("7.00"), account.getId());

        Specification<Transaction> merchantSpec = TransactionSpecifications.buildQuery(
                ledgerId, null, null, null, null, null, null, null,
                null, "星%克", null, null
        );
        assertThat(transactionRepository.findAll(merchantSpec))
                .extracting(Transaction::getId)
                .containsExactly(literal.getId());

        Specification<Transaction> keywordSpec = TransactionSpecifications.buildQuery(
                ledgerId, "%", null, null, null, null, null, null,
                null, null, null, null
        );
        assertThat(transactionRepository.findAll(keywordSpec))
                .extracting(Transaction::getId)
                .containsExactly(literal.getId());
    }

    private Account account(String name) {
        Account account = new Account();
        account.setName(name);
        account.setType(AccountType.VIRTUAL);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setLedgerId(ledgerId);
        account.setCreatedBy("spec-user");
        return accountRepository.save(account);
    }

    private Transaction transaction(String description, String merchant, BigDecimal amount, Long accountId) {
        Transaction tx = new Transaction();
        tx.setLedgerId(ledgerId);
        tx.setAmount(amount);
        tx.setType(TransactionType.EXPENSE);
        tx.setDescription(description);
        tx.setOriginalText(description);
        tx.setParsedMerchant(merchant);
        tx.setTransactionDate(LocalDate.now());
        tx.setCategoryEntity(childCategory1);
        tx.setAccountId(accountId);
        return transactionRepository.save(tx);
    }
}
