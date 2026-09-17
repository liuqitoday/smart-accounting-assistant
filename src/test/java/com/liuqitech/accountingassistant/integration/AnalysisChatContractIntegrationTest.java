package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPeriod;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisQuery;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.AnalysisChatMessage;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.AnalysisChatMessageRepository;
import com.liuqitech.accountingassistant.repository.AnalysisUsageDailyRepository;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.LedgerRepository;
import com.liuqitech.accountingassistant.repository.UserRepository;
import com.liuqitech.accountingassistant.service.AIParserService;
import com.liuqitech.accountingassistant.service.AnalysisChatService;
import com.liuqitech.accountingassistant.util.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:analysischatdb",
        "spring.sql.init.mode=never"
})
@Transactional
@WithMockUser(username = AnalysisChatContractIntegrationTest.USERNAME)
class AnalysisChatContractIntegrationTest {

    static final String USERNAME = "analysis_user";
    static final String VIEWER = "viewer";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LedgerRepository ledgerRepository;
    @Autowired private LedgerMemberRepository ledgerMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AnalysisChatMessageRepository messageRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AnalysisChatService service;
    @Autowired private AnalysisUsageDailyRepository usageDailyRepository;

    @MockitoBean
    private AIParserService aiParserService;

    private Long ledgerId;
    private Long otherLedgerId;

    @BeforeEach
    void setUp() {
        Ledger ledger = ledgerRepository.save(new Ledger("分析测试账本", USERNAME));
        ledgerId = ledger.getId();
        ledgerMemberRepository.save(new LedgerMember(ledgerId, USERNAME, LedgerRole.OWNER));
        ledgerMemberRepository.save(new LedgerMember(ledgerId, VIEWER, LedgerRole.VIEWER));

        Ledger other = ledgerRepository.save(new Ledger("另一个账本", USERNAME));
        otherLedgerId = other.getId();
        ledgerMemberRepository.save(new LedgerMember(otherLedgerId, USERNAME, LedgerRole.OWNER));

        User owner = new User();
        owner.setUsername(USERNAME);
        owner.setPassword("unused-test-password");
        owner.setDefaultLedgerId(ledgerId);
        userRepository.save(owner);

        User viewer = new User();
        viewer.setUsername(VIEWER);
        viewer.setPassword("unused-test-password");
        viewer.setDefaultLedgerId(ledgerId);
        userRepository.save(viewer);

        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class)))
            .thenReturn(clarificationPlan());
    }

    @Test
    void chatReturnsEnvelopeWithUserAndAssistantMessages() throws Exception {
        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("本月花了多少")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userMessage.content").value("本月花了多少"))
                .andExpect(jsonPath("$.data.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.data.assistantMessage.role").value("ASSISTANT"));
    }

    @Test
    void blankQuestionReturns400WithEnvelope() throws Exception {
        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void messagesReturnsPagedHistoryNewestFirst() throws Exception {
        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("第一问")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/analysis/messages")
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.content[1].role").value("USER"));
    }

    @Test
    void clearDeletesOnlyOwnLedgerStream() throws Exception {
        messageRepository.save(new AnalysisChatMessage(ledgerId, USERNAME, "USER", "留言A", null, "OK"));
        messageRepository.save(new AnalysisChatMessage(otherLedgerId, USERNAME, "USER", "留言B", null, "OK"));

        mockMvc.perform(delete("/api/analysis/messages").with(csrf())
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/analysis/messages").header("X-Ledger-Id", ledgerId))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/analysis/messages").header("X-Ledger-Id", otherLedgerId))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithAnonymousUser
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("本月花了多少")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatReturnsSchemaVersionTwoOnValidPlan() throws Exception {
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class)))
            .thenReturn(validAggregatePlan());

        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("本月支出")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assistantMessage.payload").value(
                    org.hamcrest.Matchers.containsString("\"schemaVersion\":2")));
    }

    @Test
    void viewerCanChatAndClearOwnMessagesButCannotWriteTransaction() throws Exception {
        long actualCategoryId = categoryRepository.save(testExpenseCategory()).getId();
        long actualAccountId = accountRepository.save(testAccount()).getId();
        mockMvc.perform(post("/api/analysis/chat").with(user(VIEWER)).with(csrf())
                .header("X-Ledger-Id", ledgerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"本月支出\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/analysis/messages").with(user(VIEWER)).with(csrf())
                .header("X-Ledger-Id", ledgerId))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/transactions/save").with(user(VIEWER)).with(csrf())
                .header("X-Ledger-Id", ledgerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validSaveTransactionJson(actualCategoryId, actualAccountId)))
            .andExpect(status().isForbidden());
    }

    @Test
    void messagesPageSizeIsClampedToFifty() throws Exception {
        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("第一问")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/analysis/messages")
                        .param("page", "0")
                        .param("size", "200")
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.number").value(0));
    }

    @Test
    void clearDoesNotResetDailyUsage() throws Exception {
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class)))
            .thenReturn(validAggregatePlan());

        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("本月支出")))
                .andExpect(status().isOk());

        int before = usageDailyRepository.findByUserIdAndBusinessDate(USERNAME, AppClock.today())
            .map(daily -> daily.getSuccessfulCount() + daily.getClarificationCount() + daily.getFailedCount())
            .orElse(0);
        org.assertj.core.api.Assertions.assertThat(before).isGreaterThan(0);

        mockMvc.perform(delete("/api/analysis/messages").with(csrf())
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isOk());

        int after = usageDailyRepository.findByUserIdAndBusinessDate(USERNAME, AppClock.today())
            .map(daily -> daily.getSuccessfulCount() + daily.getClarificationCount() + daily.getFailedCount())
            .orElse(0);
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
    }

    @Test
    void noDataPayloadKeepsNamedStatus() throws Exception {
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class)))
            .thenReturn(validAggregatePlan());

        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("本月支出")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assistantMessage.payload").value(
                    org.hamcrest.Matchers.containsString("\"status\":\"NO_DATA\"")))
                .andExpect(jsonPath("$.data.assistantMessage.payload").value(
                    org.hamcrest.Matchers.containsString("\"schemaVersion\":2")));
    }

    @Test
    void invalidPlanReturnsNamedErrorStatus() throws Exception {
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class)))
            .thenReturn(invalidPlan());

        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("乱写一个计划")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assistantMessage.payload").value(
                    org.hamcrest.Matchers.containsString("\"status\":\"INVALID_PLAN\"")));
    }

    @Test
    void aiFailureMapsToFailedAssistantStatus() throws Exception {
        when(aiParserService.parseStructured(anyString(), eq(RawAnalysisPlan.class)))
            .thenThrow(new RuntimeException("AI 超时"));

        mockMvc.perform(post("/api/analysis/chat").with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("本月支出")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assistantMessage.status").value("FAILED"))
                .andExpect(jsonPath("$.data.assistantMessage.content").value(
                    org.hamcrest.Matchers.containsString("AI")));
    }

    @Test
    void v1HistoryPayloadStillReturnsUnchangedAndV2HistoryParsesTogether() {
        messageRepository.save(new AnalysisChatMessage(ledgerId, USERNAME, "ASSISTANT", "旧结果",
            "{\"plan\":{},\"results\":{\"queries\":[]}}", "OK"));
        messageRepository.save(new AnalysisChatMessage(ledgerId, USERNAME, "ASSISTANT", "新结果",
            "{\"schemaVersion\":2,\"results\":[],\"status\":\"NO_DATA\"}", "OK"));

        var page = service.getMessages(ledgerId, USERNAME, 0, 20);

        org.assertj.core.api.Assertions.assertThat(page.getContent())
            .extracting(dto -> dto.getPayload())
            .contains("{\"plan\":{},\"results\":{\"queries\":[]}}",
                "{\"schemaVersion\":2,\"results\":[],\"status\":\"NO_DATA\"}");
    }

    private String chatBody(String question) {
        return "{\"question\":\"" + question + "\"}";
    }

    private RawAnalysisPlan clarificationPlan() {
        RawAnalysisPlan plan = new RawAnalysisPlan();
        plan.setOperation("AGGREGATE");
        plan.setTitle("澄清");
        RawAnalysisQuery query = new RawAnalysisQuery();
        query.setId("q1");
        query.setKind("AGGREGATE");
        query.setTitle("哪个最多");
        RawAnalysisPeriod period = new RawAnalysisPeriod();
        query.setPeriod(period);
        plan.setQueries(List.of(query));
        return plan;
    }

    private RawAnalysisPlan invalidPlan() {
        RawAnalysisPlan plan = new RawAnalysisPlan();
        plan.setOperation("NOT_A_REAL_OPERATION");
        plan.setTitle("非法计划");
        RawAnalysisQuery query = new RawAnalysisQuery();
        query.setId("q1");
        query.setKind("NOT_A_KIND");
        RawAnalysisPeriod period = new RawAnalysisPeriod();
        period.setPreset("CURRENT_MONTH");
        query.setPeriod(period);
        plan.setQueries(List.of(query));
        return plan;
    }

    private RawAnalysisPlan validAggregatePlan() {
        RawAnalysisPeriod period = new RawAnalysisPeriod();
        period.setPreset("CURRENT_MONTH");
        RawAnalysisQuery query = new RawAnalysisQuery();
        query.setId("q1");
        query.setKind("AGGREGATE");
        query.setMetric("SUM");
        query.setTransactionType("EXPENSE");
        query.setPeriod(period);
        query.setLimit(10);
        query.setTitle("本月支出");
        RawAnalysisPlan plan = new RawAnalysisPlan();
        plan.setOperation("AGGREGATE");
        plan.setTitle("本月支出");
        plan.setQueries(List.of(query));
        return plan;
    }

    private Category testExpenseCategory() {
        return new Category("测试分类", null, 1, TransactionType.EXPENSE, "测试");
    }

    private Account testAccount() {
        Account account = new Account();
        account.setName("测试账户");
        account.setType(AccountType.VIRTUAL);
        account.setLedgerId(ledgerId);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setActive(true);
        account.setCreatedBy(USERNAME);
        return account;
    }

    private String validSaveTransactionJson(long categoryId, long accountId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "amount", "1.00", "type", "EXPENSE", "transactionDate", "2026-08-25",
            "categoryId", categoryId, "accountId", accountId, "description", "测试"));
    }
}
