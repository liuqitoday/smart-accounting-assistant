package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.LedgerRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:transactiondb",
        "spring.sql.init.mode=never"
})
@Transactional
@WithMockUser(username = TransactionIntegrationTest.USERNAME)
class TransactionIntegrationTest {

    static final String USERNAME = "transaction_user";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LedgerRepository ledgerRepository;
    @Autowired
    private LedgerMemberRepository ledgerMemberRepository;
    @Autowired
    private AccountRepository accountRepository;

    private Long ledgerId;
    private Category expenseCategory;
    private Category incomeCategory;
    private Category transferCategory;

    @BeforeEach
    void setUp() {
        Ledger ledger = ledgerRepository.save(new Ledger("集成测试账本", USERNAME));
        ledgerId = ledger.getId();
        ledgerMemberRepository.save(new LedgerMember(ledgerId, USERNAME, LedgerRole.OWNER));

        User user = new User();
        user.setUsername(USERNAME);
        user.setPassword("unused-test-password");
        user.setDefaultLedgerId(ledgerId);
        userRepository.save(user);

        expenseCategory = category("测试支出", TransactionType.EXPENSE);
        incomeCategory = category("测试收入", TransactionType.INCOME);
        transferCategory = category("转账", TransactionType.TRANSFER);
    }

    @Test
    void authenticatedUserCanReadCategories() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void regularTransactionWithMatchingCategoryCanBeSaved() throws Exception {
        mockMvc.perform(post("/api/transactions/save")
                        .with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveRequest(TransactionType.EXPENSE, expenseCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.categoryId").value(expenseCategory.getId()));

        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void regularTransactionRejectsCategoryWithDifferentType() throws Exception {
        mockMvc.perform(post("/api/transactions/save")
                        .with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveRequest(TransactionType.EXPENSE, incomeCategory.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("分类类型与交易类型不一致")));

        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void regularTransactionEndpointRejectsTransfer() throws Exception {
        mockMvc.perform(post("/api/transactions/save")
                        .with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveRequest(TransactionType.TRANSFER, transferCategory.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("转账必须使用转账专用接口")));

        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void regularUpdateEndpointRejectsExistingTransferWithoutMutatingIt() throws Exception {
        Transaction transfer = new Transaction();
        transfer.setLedgerId(ledgerId);
        transfer.setCreatedBy(USERNAME);
        transfer.setAmount(new BigDecimal("88.00"));
        transfer.setType(TransactionType.TRANSFER);
        transfer.setDescription("测试转账");
        transfer.setOriginalText("测试转账");
        transfer.setCategoryEntity(transferCategory);
        transfer.setTransactionDate(LocalDate.of(2026, 7, 10));
        transfer.setAccountId(101L);
        transfer.setCounterAccountId(102L);
        transfer = transactionRepository.saveAndFlush(transfer);

        Map<String, Object> update = Map.of(
                "amount", "99.00",
                "type", "EXPENSE",
                "description", "不应生效",
                "transactionDate", "2026-07-10",
                "categoryId", expenseCategory.getId()
        );

        mockMvc.perform(put("/api/transactions/{id}", transfer.getId())
                        .with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("转账不能通过普通交易接口修改")));

        Transaction unchanged = transactionRepository.findById(transfer.getId()).orElseThrow();
        assertThat(unchanged.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(unchanged.getAmount()).isEqualByComparingTo("88.00");
        assertThat(unchanged.getAccountId()).isEqualTo(101L);
        assertThat(unchanged.getCounterAccountId()).isEqualTo(102L);
    }

    @Test
    void listBindsAccountMerchantAndAmountFilters() throws Exception {
        Account coffeeAccount = account("支付宝");
        Account otherAccount = account("银行卡");
        Transaction coffee = seededTransaction("咖啡拿铁", "咖啡", "68.00", coffeeAccount.getId());
        seededTransaction("午餐盒饭", "食堂", "30.00", otherAccount.getId());

        mockMvc.perform(get("/api/transactions")
                        .header("X-Ledger-Id", ledgerId)
                        .param("accountId", String.valueOf(coffeeAccount.getId()))
                        .param("merchant", "咖啡")
                        .param("minAmount", "50.00")
                        .param("maxAmount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(coffee.getId()))
                .andExpect(jsonPath("$.data.content[0].parsedMerchant").value("咖啡"));
    }

    @Test
    void exportBindsAccountMerchantAndAmountFilters() throws Exception {
        Account coffeeAccount = account("支付宝");
        Account otherAccount = account("银行卡");
        seededTransaction("咖啡拿铁", "咖啡", "68.00", coffeeAccount.getId());
        seededTransaction("午餐盒饭", "食堂", "30.00", otherAccount.getId());

        String csv = mockMvc.perform(get("/api/transactions/export")
                        .header("X-Ledger-Id", ledgerId)
                        .param("accountId", String.valueOf(coffeeAccount.getId()))
                        .param("merchant", "咖啡")
                        .param("minAmount", "50.00")
                        .param("maxAmount", "100.00"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).contains("咖啡");
        assertThat(csv).doesNotContain("食堂");
    }

    @Test
    void listRejectsCrossLedgerAccountId() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("X-Ledger-Id", ledgerId)
                        .param("accountId", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listRejectsMinAmountGreaterThanMaxAmount() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("X-Ledger-Id", ledgerId)
                        .param("minAmount", "100.00")
                        .param("maxAmount", "50.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Account account(String name) {
        Account account = new Account();
        account.setName(name);
        account.setType(AccountType.VIRTUAL);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setLedgerId(ledgerId);
        account.setCreatedBy(USERNAME);
        return accountRepository.save(account);
    }

    private Transaction seededTransaction(String description, String merchant, String amount, Long accountId) {
        Transaction tx = new Transaction();
        tx.setLedgerId(ledgerId);
        tx.setCreatedBy(USERNAME);
        tx.setAmount(new BigDecimal(amount));
        tx.setType(TransactionType.EXPENSE);
        tx.setDescription(description);
        tx.setOriginalText(description);
        tx.setParsedMerchant(merchant);
        tx.setTransactionDate(LocalDate.of(2026, 8, 10));
        tx.setCategoryEntity(expenseCategory);
        tx.setAccountId(accountId);
        return transactionRepository.saveAndFlush(tx);
    }

    private Category category(String name, TransactionType type) {
        Category category = new Category();
        category.setName(name);
        category.setLevel(1);
        category.setType(type);
        return categoryRepository.save(category);
    }

    private String saveRequest(TransactionType type, Long categoryId) throws Exception {
        Map<String, Object> request = Map.of(
                "amount", "35.00",
                "type", type.name(),
                "description", "接口测试交易",
                "originalText", "接口测试交易",
                "categoryId", categoryId,
                "transactionDate", "2026-07-10"
        );
        return objectMapper.writeValueAsString(request);
    }
}
