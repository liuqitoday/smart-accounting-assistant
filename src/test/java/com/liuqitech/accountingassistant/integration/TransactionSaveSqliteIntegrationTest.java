package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.SaveTransactionRequest;
import com.liuqitech.accountingassistant.dto.TransactionParseResponse;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.LedgerRepository;
import com.liuqitech.accountingassistant.repository.TagRepository;
import com.liuqitech.accountingassistant.service.TransactionSaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the real SQLite unique key and transactions, including simultaneous retry requests. */
@SpringBootTest(properties = {
        "spring.config.import=", "spring.sql.init.mode=never", "spring.ai.openai.api-key=disabled",
        "spring.ai.openai.base-url=http://127.0.0.1:9", "app.recurring-bills.cron=-",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect"
})
@AutoConfigureMockMvc
@WithMockUser(username = TransactionSaveSqliteIntegrationTest.USER)
class TransactionSaveSqliteIntegrationTest {
    static final String USER = "save_contract_user";
    static final Path DATABASE = temporaryDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE
                + "?journal_mode=WAL&busy_timeout=5000&synchronous=NORMAL");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TransactionSaveService saves;
    @Autowired JdbcTemplate jdbc;
    @Autowired LedgerRepository ledgers;
    @Autowired LedgerMemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired TagRepository tags;
    @Autowired AccountRepository accounts;

    Long ledgerId;
    Long categoryId;
    Long tagId;
    Long systemTagId;

    @BeforeEach
    void fixtures() {
        ledgerId = ledgers.save(new Ledger("保存测试", USER)).getId();
        members.save(new LedgerMember(ledgerId, USER, LedgerRole.OWNER));
        Category category = new Category();
        category.setName("保存支出" + ledgerId);
        category.setLevel(1);
        category.setType(TransactionType.EXPENSE);
        categoryId = categories.save(category).getId();
        Tag local = new Tag("本账本标签", USER);
        local.setLedgerId(ledgerId);
        tagId = tags.save(local).getId();
        Tag system = new Tag("系统标签" + ledgerId, USER);
        system.setSystem(true);
        systemTagId = tags.save(system).getId();
    }

    @Test
    void savesTagsAtomicallyAndReplaysOriginalResponseEvenAfterAnEdit() throws Exception {
        SaveTransactionRequest request = request(UUID.randomUUID().toString());
        request.setTagIds(List.of(tagId, systemTagId, tagId));
        JsonNode first = postSave(request, 200);
        long id = first.path("data").path("id").asLong();
        assertThat(first.path("data").path("tags")).hasSize(2);

        // A delayed retry must acknowledge the original save, not create a second row or reset edits.
        jdbc.update("UPDATE transactions SET description = '后来编辑' WHERE id = ?", id);
        request.setAmount(new BigDecimal("12.0"));
        request.setTagIds(List.of(systemTagId, tagId));
        JsonNode replay = postSave(request, 200);
        assertThat(replay.path("data")).isEqualTo(first.path("data"));
        assertThat(countTransactions()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT description FROM transactions WHERE id = ?", String.class, id))
                .isEqualTo("后来编辑");
    }

    @Test
    void changedPayloadForSameKeyIs400AndKeyIsScopedToUserAndLedger() throws Exception {
        String key = UUID.randomUUID().toString();
        SaveTransactionRequest request = request(key);
        postSave(request, 200);
        request.setAmount(new BigDecimal("13"));
        JsonNode conflict = postSave(request, 400);
        assertThat(conflict.path("errorCode").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(countTransactions()).isEqualTo(1);

        TransactionParseResponse otherUser = saves.save(request(key), ledgerId, "another_user");
        Long otherLedger = ledgers.save(new Ledger("另一个账本", USER)).getId();
        TransactionParseResponse otherBook = saves.save(request(key), otherLedger, USER);
        assertThat(otherUser.getId()).isNotEqualTo(otherBook.getId());
        assertThat(countTransactions()).isEqualTo(2);
    }

    @Test
    void invalidTagsRollBackReceiptAndTransactionSoTheRequestCanBeCorrected() throws Exception {
        Tag foreign = new Tag("另一账本标签", USER);
        foreign.setLedgerId(ledgers.save(new Ledger("外部账本", USER)).getId());
        Long foreignId = tags.save(foreign).getId();
        SaveTransactionRequest request = request(UUID.randomUUID().toString());
        request.setTagIds(List.of(tagId, foreignId));
        postSave(request, 404);
        assertThat(countTransactions()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_save_receipts WHERE ledger_id = ?",
                Integer.class, ledgerId)).isZero();

        request.setTagIds(List.of(tagId, systemTagId));
        postSave(request, 200);
        assertThat(countTransactions()).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyRequestsCommitExactlyOneTransaction() throws Exception {
        String key = UUID.randomUUID().toString();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<TransactionParseResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    SaveTransactionRequest request = request(key);
                    request.setTagIds(List.of(tagId, systemTagId));
                    return saves.save(request, ledgerId, USER);
                }));
            }
            start.countDown();
            List<Long> ids = new ArrayList<>();
            for (Future<TransactionParseResponse> future : futures) ids.add(future.get(15, TimeUnit.SECONDS).getId());
            assertThat(ids).hasSize(4).containsOnly(ids.get(0));
            assertThat(countTransactions()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_tags WHERE transaction_id = ?",
                    Integer.class, ids.get(0))).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void editingTagsIsAtomicAndOmittedTagsRemainCompatible() throws Exception {
        SaveTransactionRequest create = request(null);
        create.setTagIds(List.of(tagId));
        long id = postSave(create, 200).path("data").path("id").asLong();
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "amount", "20", "type", "EXPENSE", "description", "编辑后",
                "transactionDate", "2026-11-10", "categoryId", categoryId));
        mvc.perform(put("/api/transactions/{id}", id).with(csrf()).header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tags.length()").value(1));
        body.put("tagIds", List.of(Long.MAX_VALUE));
        body.put("amount", "99");
        mvc.perform(put("/api/transactions/{id}", id).with(csrf()).header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT amount FROM transactions WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("20");
        body.put("tagIds", List.of());
        mvc.perform(put("/api/transactions/{id}", id).with(csrf()).header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tags.length()").value(0));
    }

    @Test
    void uuidValidationAndLegacyClientsHaveDefinedBehavior() throws Exception {
        postSave(request("not-a-uuid"), 400);
        postSave(request(null), 200);
        postSave(request(null), 200);
        assertThat(countTransactions()).isEqualTo(2);
    }

    @Test
    void accountOptionsOnlyExposeCurrentLedgerAndMemberDefault() throws Exception {
        Account account = new Account();
        account.setName("日常现金");
        account.setType(AccountType.CASH);
        account.setLedgerId(ledgerId);
        account = accounts.save(account);
        LedgerMember member = members.findByLedgerIdAndUsername(ledgerId, USER).orElseThrow();
        member.setDefaultAccountId(account.getId());
        members.save(member);
        Account foreign = new Account();
        foreign.setName("其他账本现金");
        foreign.setType(AccountType.CASH);
        foreign.setLedgerId(ledgers.save(new Ledger("其他账户账本", USER)).getId());
        accounts.save(foreign);

        mvc.perform(get("/api/accounts/options").header("X-Ledger-Id", ledgerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(account.getId()))
                .andExpect(jsonPath("$.data[0].default").value(true))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].currentBalance").doesNotExist());
    }

    private JsonNode postSave(SaveTransactionRequest request, int status) throws Exception {
        String json = mvc.perform(post("/api/transactions/save").with(csrf()).header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request)))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private SaveTransactionRequest request(String key) {
        SaveTransactionRequest request = new SaveTransactionRequest();
        request.setAmount(new BigDecimal("12.00"));
        request.setType(TransactionType.EXPENSE);
        request.setDescription("午饭");
        request.setOriginalText("午饭12元");
        request.setCategoryId(categoryId);
        request.setTransactionDate(LocalDate.of(2026, 11, 10));
        request.setClientRequestId(key);
        return request;
    }

    private int countTransactions() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE ledger_id = ?", Integer.class, ledgerId);
    }

    private static Path temporaryDatabase() {
        try {
            Path file = Files.createTempDirectory("accounting-save-contract-").resolve("test.db");
            file.toFile().deleteOnExit();
            return file;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
