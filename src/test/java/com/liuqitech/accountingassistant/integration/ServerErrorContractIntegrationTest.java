package com.liuqitech.accountingassistant.integration;

import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.LedgerRepository;
import com.liuqitech.accountingassistant.repository.UserRepository;
import com.liuqitech.accountingassistant.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 服务端意外异常必须返回 HTTP 500（而不是把锅甩给客户端的 400），
 * 且响应体为统一的 ApiResponse 结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:servererrordb",
        "spring.sql.init.mode=never"
})
@Transactional
@WithMockUser(username = ServerErrorContractIntegrationTest.USERNAME)
class ServerErrorContractIntegrationTest {

    static final String USERNAME = "server_error_user";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LedgerRepository ledgerRepository;
    @Autowired
    private LedgerMemberRepository ledgerMemberRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private TransactionService transactionService;

    private Long ledgerId;

    @BeforeEach
    void setUp() {
        Ledger ledger = ledgerRepository.save(new Ledger("异常契约账本", USERNAME));
        ledgerId = ledger.getId();
        ledgerMemberRepository.save(new LedgerMember(ledgerId, USERNAME, LedgerRole.OWNER));

        User user = new User();
        user.setUsername(USERNAME);
        user.setPassword("unused-test-password");
        user.setDefaultLedgerId(ledgerId);
        userRepository.save(user);
    }

    @Test
    void unexpectedServiceErrorReturns500WithApiResponseBody() throws Exception {
        when(transactionService.queryTransactions(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("模拟服务故障"));

        mockMvc.perform(get("/api/transactions")
                        .header("X-Ledger-Id", ledgerId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RUNTIME_ERROR"))
                // 安全契约：内部异常 message（可能含 SQL/路径等）不回显，只返回固定文案
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }
}
