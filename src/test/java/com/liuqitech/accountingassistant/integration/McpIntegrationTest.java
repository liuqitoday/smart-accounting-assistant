package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.entity.*;
import com.liuqitech.accountingassistant.enums.*;
import com.liuqitech.accountingassistant.mcp.McpConnectionService;
import com.liuqitech.accountingassistant.repository.*;
import com.liuqitech.accountingassistant.service.LedgerService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real SQLite, actual MCP JSON-RPC handler, independent SDK client, and browser management API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.sql.init.mode=never", "spring.ai.openai.api-key=disabled",
        "spring.ai.openai.base-url=http://127.0.0.1:9", "app.recurring-bills.cron=-", "app.mcp.cleanup-cron=-",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect"
})
@AutoConfigureMockMvc
class McpIntegrationTest {
    static final Path DATABASE = database();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE + "?journal_mode=WAL&busy_timeout=5000&synchronous=NORMAL");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired McpConnectionService connections;
    @Autowired LedgerService ledgerService;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired LedgerRepository ledgers;
    @Autowired LedgerMemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired AccountRepository accounts;
    @LocalServerPort int port;
    String username;
    Long ledgerId, otherLedger, categoryId, accountId;
    McpConnectionService.Issued issued;

    @BeforeEach void fixtures() {
        username = "mcp_" + UUID.randomUUID().toString().substring(0, 12);
        User user = new User(); user.setUsername(username); user.setPassword("unused"); users.save(user);
        ledgerId = ledgers.save(new Ledger("生活账本", username)).getId();
        otherLedger = ledgers.save(new Ledger("未授权账本", username)).getId();
        members.save(new LedgerMember(ledgerId, username, LedgerRole.OWNER));
        members.save(new LedgerMember(otherLedger, username, LedgerRole.OWNER));
        Category category = new Category(); category.setName("餐饮" + ledgerId); category.setType(TransactionType.EXPENSE); category.setLevel(1);
        categoryId = categories.save(category).getId();
        Account account = new Account(); account.setName("微信"); account.setType(AccountType.CASH); account.setLedgerId(ledgerId);
        accountId = accounts.save(account).getId();
        issued = issue(true);
    }

    @Test void realSdkNegotiatesDiscoversAndCallsSixTools() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp").customizeRequest(b -> b.header("Authorization", "Bearer " + issued.token())).build();
        try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            var initialized = client.initialize();
            assertThat(initialized.serverInfo().name()).isEqualTo("accounting-assistant");
            assertThat(client.listTools().tools()).hasSize(6);
            var result = client.callTool(new McpSchema.CallToolRequest("accounting_get_context", Map.of()));
            assertThat(result.isError()).isFalse();
            assertThat(mapper.valueToTree(result.structuredContent()).path("data").path("ledgers")).hasSize(1);
        }
    }

    @Test void bearerIsMandatoryCookiesDoNotAuthenticateAndManagementRequiresSessionAndCsrf() throws Exception {
        mvc.perform(post("/mcp").with(user(username)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(rpc("tools/list", Map.of())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp").header("Authorization", "Bearer wrong").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/mcp/connections").header("Authorization", "Bearer " + issued.token())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/mcp/connections").with(user(username)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/mcp/connections").with(user(username))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data[0].token").doesNotExist()).andExpect(jsonPath("$.data[0].tokenHash").doesNotExist());
        mvc.perform(get("/api/mcp/connections/{id}/calls", issued.connection().id()).with(user("someone_else")))
                .andExpect(status().isNotFound());
    }

    @Test void browserCanCreateRotateAndRevokeConnectionAndNoSecretIsStored() throws Exception {
        var payload = Map.of("name", "只读助手", "ledgerIds", List.of(ledgerId), "defaultLedgerId", ledgerId, "writable", false, "expiresInDays", 30);
        var response = mvc.perform(post("/api/mcp/connections").with(user(username)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))).andExpect(status().isOk()).andReturn();
        var created = mapper.readTree(response.getResponse().getContentAsString()).path("data");
        String id = created.path("connection").path("id").asText();
        String token = created.path("token").asText();
        assertThat(connections.authenticate(token)).isPresent();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM mcp_connections WHERE id = ?", String.class, id)).hasSize(64).isNotEqualTo(token);
        var rotated = mvc.perform(post("/api/mcp/connections/{id}/rotate", id).with(user(username)).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        String replacement = mapper.readTree(rotated.getResponse().getContentAsString()).path("data").path("token").asText();
        assertThat(connections.authenticate(token)).isEmpty();
        assertThat(connections.authenticate(replacement)).isPresent();
        mvc.perform(post("/api/mcp/connections/{id}/revoke", id).with(user(username)).with(csrf())).andExpect(status().isOk());
        assertThat(connections.authenticate(replacement)).isEmpty();
    }

    @Test void contextAndOptionsAreScopedAndDoNotExposeBalances() throws Exception {
        JsonNode context = call(issued.token(), "accounting_get_context", Map.of());
        assertThat(context.path("data").path("ledgers")).hasSize(1);
        assertThat(context.path("data").path("timezone").asText()).isEqualTo("Asia/Shanghai");
        var options = call(issued.token(), "accounting_get_ledger_options", Map.of("ledgerId", ledgerId));
        assertThat(options.path("data").path("accounts").get(0).has("currentBalance")).isFalse();
        assertError(call(issued.token(), "accounting_get_ledger_options", Map.of("ledgerId", otherLedger)), "FORBIDDEN");
    }

    @Test void writesReplayExactlyOnceAndKeepAtomicProvenanceDespiteChangedOptions() throws Exception {
        var args = transaction();
        var first = call(issued.token(), "accounting_create_transaction", args);
        assertThat(first.path("ok").asBoolean()).isTrue();
        assertThat(first.path("data").path("amount").asText()).isEqualTo("35.00");
        long id = first.path("data").path("transactionId").asLong();
        jdbc.update("UPDATE accounts SET active = false WHERE id = ?", accountId);
        jdbc.update("UPDATE transactions SET description = '后续编辑' WHERE id = ?", id);
        assertThat(call(issued.token(), "accounting_create_transaction", args)).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE ledger_id = ?", Integer.class, ledgerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcp_call_logs WHERE transaction_id = ? AND outcome = 'CREATED'", Integer.class, id)).isEqualTo(1);
        args.put("amount", "36.00");
        assertError(call(issued.token(), "accounting_create_transaction", args), "IDEMPOTENCY_CONFLICT");
    }

    @Test void readOnlyDowngradedAndRemovedMembershipCannotWriteOrReplay() throws Exception {
        var args = transaction();
        assertError(call(issue(false).token(), "accounting_create_transaction", args), "FORBIDDEN");
        call(issued.token(), "accounting_create_transaction", args);
        jdbc.update("UPDATE ledger_members SET role = 'VIEWER' WHERE ledger_id = ? AND username = ?", ledgerId, username);
        assertError(call(issued.token(), "accounting_create_transaction", args), "FORBIDDEN");
        jdbc.update("DELETE FROM ledger_members WHERE ledger_id = ? AND username = ?", ledgerId, username);
        assertError(call(issued.token(), "accounting_get_ledger_options", Map.of("ledgerId", ledgerId)), "FORBIDDEN");
        assertThat(call(issued.token(), "accounting_get_context", Map.of()).path("data").path("ledgers")).isEmpty();
    }

    @Test void deletingALedgerRemovesGrantsEvenWhenItsIdIsReused() throws Exception {
        var connection = connections.create(username, new McpConnectionService.CreateRequest(
                "即将删除的账本", List.of(otherLedger), otherLedger, false, 30));
        ledgerService.deleteLedger(username, otherLedger);
        jdbc.update("INSERT INTO ledgers (id, name, owner_username, is_default) VALUES (?, ?, ?, false)",
                otherLedger, "新账本", username);
        members.save(new LedgerMember(otherLedger, username, LedgerRole.OWNER));
        var context = call(connection.token(), "accounting_get_context", Map.of()).path("data");
        assertThat(context.path("ledgers")).isEmpty();
        assertThat(context.path("defaultLedgerId").isNull()).isTrue();
        assertError(call(connection.token(), "accounting_get_ledger_options", Map.of("ledgerId", otherLedger)), "FORBIDDEN");
    }

    @Test void invalidFieldsReferencesAndUnknownFiltersNeverSaveOrSilentlyWiden() throws Exception {
        for (Map<String, Object> override : List.<Map<String, Object>>of(Map.of("amount", "1.001"), Map.of("amount", 35), Map.of("amount", "0"),
                Map.of("transactionDate", "2026-02-30"), Map.of("type", "TRANSFER"), Map.of("currency", "USD"),
                Map.of("createdBy", "someone_else"), Map.of("categoryId", 999999), Map.of("tagIds", List.of(999999)))) {
            var args = transaction(); args.putAll(override);
            assertThat(call(issued.token(), "accounting_create_transaction", args).path("ok").asBoolean()).isFalse();
        }
        jdbc.update("UPDATE accounts SET active = false WHERE id = ?", accountId);
        assertError(call(issued.token(), "accounting_create_transaction", transaction()), "VALIDATION_ERROR");
        var query = range(); query.put("unsupportedFilter", "ignored?");
        assertError(call(issued.token(), "accounting_list_transactions", query), "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE ledger_id = ?", Integer.class, ledgerId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_save_receipts WHERE ledger_id = ?", Integer.class, ledgerId)).isZero();
    }

    @Test void detailsAndStatisticsRespectFiltersAndPaginationAndSoftDeletion() throws Exception {
        var first = call(issued.token(), "accounting_create_transaction", transaction());
        long id = first.path("data").path("transactionId").asLong();
        call(issued.token(), "accounting_create_transaction", transaction());
        var query = range(); query.put("pageSize", 1);
        var list = call(issued.token(), "accounting_list_transactions", query).path("data");
        assertThat(list.path("items")).hasSize(1);
        assertThat(list.path("totalElements").asInt()).isEqualTo(2);
        assertThat(list.path("hasMore").asBoolean()).isTrue();
        var stats = range(); stats.put("mode", "summary");
        assertThat(call(issued.token(), "accounting_get_statistics", stats).path("data").path("result").path("totalExpense").asText()).isEqualTo("70.00");
        stats.put("keyword", "no-match");
        assertThat(call(issued.token(), "accounting_get_statistics", stats).path("data").path("result").path("transactionCount").asInt()).isZero();
        jdbc.update("UPDATE transactions SET parsed_merchant = '独特商户' WHERE id = ?", id);
        stats.put("keyword", "独特商户");
        assertThat(call(issued.token(), "accounting_get_statistics", stats).path("data").path("result").path("transactionCount").asInt()).isZero();
        query.put("keyword", "独特商户");
        assertThat(call(issued.token(), "accounting_list_transactions", query).path("data").path("totalElements").asInt()).isZero();
        stats.remove("keyword"); stats.put("merchant", "独特商户");
        query.remove("keyword"); query.put("merchant", "独特商户");
        assertThat(call(issued.token(), "accounting_get_statistics", stats).path("data").path("result").path("transactionCount").asInt()).isEqualTo(1);
        assertThat(call(issued.token(), "accounting_list_transactions", query).path("data").path("totalElements").asInt()).isEqualTo(1);
        stats.remove("merchant");
        stats.remove("keyword"); stats.put("mode", "by_category"); stats.put("type", "EXPENSE");
        assertThat(call(issued.token(), "accounting_get_statistics", stats).path("data").path("result").path("totalAmount").asText()).isEqualTo("70.00");
        stats.put("mode", "trend"); stats.put("granularity", "day");
        assertThat(call(issued.token(), "accounting_get_statistics", stats).path("ok").asBoolean()).isTrue();
        jdbc.update("UPDATE transactions SET deleted_at = '2026-11-20 00:00:00' WHERE id = ?", id);
        assertError(call(issued.token(), "accounting_get_transaction", Map.of("ledgerId", ledgerId, "transactionId", id)), "NOT_FOUND");
        var another = connections.create(username, new McpConnectionService.CreateRequest("other", List.of(otherLedger), null, false, 30));
        assertError(call(another.token(), "accounting_get_transaction", Map.of("ledgerId", otherLedger, "transactionId", id)), "NOT_FOUND");
    }

    @Test void revokedExpiredTokensAndHostOriginAndSizeAreEnforced() throws Exception {
        for (String problem : List.of("origin", "host", "size")) {
            var request = post("/mcp").header("Authorization", "Bearer " + issued.token()).accept("application/json, text/event-stream").contentType(MediaType.APPLICATION_JSON);
            if (problem.equals("origin")) request.header("Origin", "https://untrusted.example");
            if (problem.equals("host")) request.with(r -> { r.setServerName("untrusted.example"); return r; });
            request.content(problem.equals("size") ? "x".repeat(65537) : rpc("tools/list", Map.of()));
            mvc.perform(request).andExpect(status().is(problem.equals("size") ? 413 : 403));
        }
        jdbc.update("UPDATE mcp_connections SET expires_at = 1 WHERE id = ?", issued.connection().id());
        mvc.perform(post("/mcp").header("Authorization", "Bearer " + issued.token()).content("{}"))
                .andExpect(status().isUnauthorized());
        var other = issue(true); connections.revoke(username, other.connection().id());
        mvc.perform(post("/mcp").header("Authorization", "Bearer " + other.token()).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void concurrentHttpRetriesDoNotDuplicateTransactionsOrCreationLogs() throws Exception {
        var args = transaction();
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<JsonNode>> tasks = List.of(() -> call(issued.token(), "accounting_create_transaction", args),
                    () -> call(issued.token(), "accounting_create_transaction", args),
                    () -> call(issued.token(), "accounting_create_transaction", args), () -> call(issued.token(), "accounting_create_transaction", args));
            List<JsonNode> results = new ArrayList<>();
            for (var future : pool.invokeAll(tasks, 15, TimeUnit.SECONDS)) results.add(future.get());
            assertThat(results).allMatch(r -> r.path("ok").asBoolean());
            assertThat(results).containsOnly(results.get(0));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE ledger_id = ?", Integer.class, ledgerId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcp_call_logs WHERE connection_id = ? AND outcome = 'CREATED'",
                    Integer.class, issued.connection().id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcp_call_logs WHERE connection_id = ? AND outcome = 'SUCCESS'",
                    Integer.class, issued.connection().id())).isEqualTo(4);
        } finally { pool.shutdownNow(); }
    }

    @Test void failedCreationAuditRollsBackTheEntryAndReceipt() throws Exception {
        jdbc.execute("CREATE TRIGGER fail_mcp_creation_audit BEFORE INSERT ON mcp_call_logs "
                + "WHEN NEW.outcome = 'CREATED' BEGIN SELECT RAISE(ABORT, 'test audit failure'); END");
        var args = transaction();
        try {
            assertError(call(issued.token(), "accounting_create_transaction", args), "INTERNAL_ERROR");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE ledger_id = ?", Integer.class, ledgerId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_save_receipts WHERE ledger_id = ?", Integer.class, ledgerId)).isZero();
        } finally { jdbc.execute("DROP TRIGGER fail_mcp_creation_audit"); }
        assertThat(call(issued.token(), "accounting_create_transaction", args).path("ok").asBoolean()).isTrue();
    }

    @Test void concurrentStatisticsShareJpaAndJdbcConnectionsWithoutExhaustingThePool() throws Exception {
        call(issued.token(), "accounting_create_transaction", transaction());
        var args = range(); args.put("mode", "summary"); args.put("categoryId", categoryId);
        var pool = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(4);
        Callable<JsonNode> query = () -> {
            start.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return call(issued.token(), "accounting_get_statistics", args);
        };
        try {
            for (var future : pool.invokeAll(List.of(query, query, query, query), 15, TimeUnit.SECONDS)) {
                var result = future.get();
                assertThat(result.path("ok").asBoolean()).isTrue();
                assertThat(result.path("data").path("result").path("totalExpense").asText()).isEqualTo("35.00");
            }
        } finally { pool.shutdownNow(); }
    }

    McpConnectionService.Issued issue(boolean write) {
        return connections.create(username, new McpConnectionService.CreateRequest("test", List.of(ledgerId), ledgerId, write, 30));
    }
    Map<String, Object> transaction() {
        return new HashMap<>(Map.of("ledgerId", ledgerId, "amount", "35.00", "currency", "CNY", "type", "EXPENSE",
                "transactionDate", "2026-11-10", "description", "午饭", "categoryId", categoryId, "accountId", accountId,
                "clientRequestId", UUID.randomUUID().toString()));
    }
    Map<String, Object> range() { return new HashMap<>(Map.of("ledgerId", ledgerId, "startDate", "2026-11-01", "endDate", "2026-11-20")); }
    String rpc(String method, Object params) throws Exception {
        return mapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params));
    }
    JsonNode call(String token, String tool, Map<String, Object> args) throws Exception {
        var result = mvc.perform(post("/mcp").header("Authorization", "Bearer " + token)
                        .accept("application/json, text/event-stream").contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("tools/call", Map.of("name", tool, "arguments", args))))
                .andExpect(status().isOk()).andReturn();
        var response = mapper.readTree(result.getResponse().getContentAsString()).path("result");
        JsonNode content = response.path("structuredContent");
        assertThat(response.path("isError").asBoolean()).isEqualTo(!content.path("ok").asBoolean());
        assertThat(mapper.readTree(response.path("content").get(0).path("text").asText())).isEqualTo(content);
        return content;
    }
    void assertError(JsonNode result, String code) { assertThat(result.path("ok").asBoolean()).isFalse(); assertThat(result.path("errorCode").asText()).isEqualTo(code); }
    static Path database() {
        try { Path path = Files.createTempFile("accounting-mcp-test-", ".db"); path.toFile().deleteOnExit(); return path; }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
