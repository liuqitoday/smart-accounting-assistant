package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.LedgerRepository;
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

import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 响应契约测试：所有 JSON 接口的错误响应必须携带统一的 ApiResponse 体，
 * 且 HTTP 状态与错误语义一致（404=不存在、400=客户端参数/业务错误）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:responsecontractdb",
        "spring.sql.init.mode=never"
})
@Transactional
@WithMockUser(username = ResponseContractIntegrationTest.USERNAME)
class ResponseContractIntegrationTest {

    static final String USERNAME = "contract_user";
    private static final long MISSING_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LedgerRepository ledgerRepository;
    @Autowired
    private LedgerMemberRepository ledgerMemberRepository;
    @Autowired
    private UserRepository userRepository;

    private Long ledgerId;

    @BeforeEach
    void setUp() {
        Ledger ledger = ledgerRepository.save(new Ledger("契约测试账本", USERNAME));
        ledgerId = ledger.getId();
        ledgerMemberRepository.save(new LedgerMember(ledgerId, USERNAME, LedgerRole.OWNER));

        User user = new User();
        user.setUsername(USERNAME);
        user.setPassword("unused-test-password");
        user.setDefaultLedgerId(ledgerId);
        userRepository.save(user);
    }

    @Test
    void getMissingTransactionReturns404WithApiResponseBody() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", MISSING_ID)
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())));
    }

    @Test
    void updateMissingTransactionReturns404WithApiResponseBody() throws Exception {
        Map<String, Object> body = Map.of(
                "amount", "10.00",
                "type", "EXPENSE",
                "description", "不存在的交易",
                "transactionDate", "2026-07-10",
                "categoryId", 1
        );

        mockMvc.perform(put("/api/transactions/{id}", MISSING_ID)
                        .with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())));
    }

    @Test
    void deleteMissingTransactionReturns404WithApiResponseBody() throws Exception {
        mockMvc.perform(delete("/api/transactions/{id}", MISSING_ID)
                        .with(csrf())
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())));
    }

    @Test
    void restoreMissingTransactionReturns404WithApiResponseBody() throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/restore", MISSING_ID)
                        .with(csrf())
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())));
    }

    @Test
    void getMissingCategoryReturns404WithApiResponseBody() throws Exception {
        mockMvc.perform(get("/api/categories/{id}", MISSING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())));
    }

    @Test
    void invalidTransactionTypeFilterReturns400WithErrorCode() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("X-Ledger-Id", ledgerId)
                        .param("type", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_ARGUMENT"))
                // 业务侧友好提示保留回显（经 BusinessException；全局 IAE 处理器已不回显原始 message）
                .andExpect(jsonPath("$.message").value("无效的交易类型: FOO，请使用 INCOME、EXPENSE 或 TRANSFER"));
    }

    @Test
    void exportWithInvalidTypeReturns400WithApiResponseBody() throws Exception {
        mockMvc.perform(get("/api/transactions/export")
                        .header("X-Ledger-Id", ledgerId)
                        .param("type", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(not(emptyOrNullString())));
    }

    /** 路径参数类型不匹配是客户端错误：必须 400，不能落进 RuntimeException 兜底变 500。 */
    @Test
    void nonNumericPathIdReturns400WithApiResponseBody() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", "notanumber")
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("参数不合法，请检查输入"));
    }

    /**
     * 回归保护：重构前后成功响应的信封结构不变。
     * 用 /recent（纯 JPA 查询）而非 /summary，后者的原生 SQLite SQL 在 H2 上无法执行。
     */
    @Test
    void statisticsRecentKeepsEnvelopeShape() throws Exception {
        mockMvc.perform(get("/api/statistics/recent")
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
