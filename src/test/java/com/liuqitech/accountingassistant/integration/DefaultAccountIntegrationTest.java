package com.liuqitech.accountingassistant.integration;

import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.repository.AccountRepository;
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

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「默认账户」端到端契约：PUT /api/accounts/default 设置或清除，
 * GET /api/accounts 用 default 字段回传，且该偏好按成员各自隔离。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:defaultaccountdb",
        "spring.sql.init.mode=never"
})
@Transactional
@WithMockUser(username = DefaultAccountIntegrationTest.USERNAME)
class DefaultAccountIntegrationTest {

    static final String USERNAME = "default_account_user";
    static final String PARTNER = "default_account_partner";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private LedgerRepository ledgerRepository;
    @Autowired
    private LedgerMemberRepository ledgerMemberRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;

    private Long ledgerId;
    private Long cashId;
    private Long bankId;

    @BeforeEach
    void setUp() {
        Ledger ledger = ledgerRepository.save(new Ledger("默认账户测试账本", USERNAME));
        ledgerId = ledger.getId();
        ledgerMemberRepository.save(new LedgerMember(ledgerId, USERNAME, LedgerRole.OWNER));
        ledgerMemberRepository.save(new LedgerMember(ledgerId, PARTNER, LedgerRole.EDITOR));

        User user = new User();
        user.setUsername(USERNAME);
        user.setPassword("unused-test-password");
        user.setDefaultLedgerId(ledgerId);
        userRepository.save(user);

        cashId = accountRepository.save(account("现金", true)).getId();
        bankId = accountRepository.save(account("银行卡", true)).getId();
    }

    @Test
    void settingDefaultAccountMarksExactlyThatAccountOnTheList() throws Exception {
        setDefault(cashId).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/accounts").header("X-Ledger-Id", ledgerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + cashId + ")].default").value(true))
                .andExpect(jsonPath("$.data[?(@.id == " + bankId + ")].default").value(false));
    }

    @Test
    void clearingDefaultAccountWithNullLeavesNoAccountMarked() throws Exception {
        setDefault(cashId).andExpect(status().isOk());

        setDefault(null).andExpect(status().isOk());

        mockMvc.perform(get("/api/accounts").header("X-Ledger-Id", ledgerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.default == true)]").isEmpty());
        assertThat(ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, USERNAME).orElseThrow()
                .getDefaultAccountId()).isNull();
    }

    @Test
    void defaultAccountIsKeptPerMemberNotPerLedger() throws Exception {
        setDefault(cashId).andExpect(status().isOk());

        assertThat(ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, USERNAME).orElseThrow()
                .getDefaultAccountId()).isEqualTo(cashId);
        assertThat(ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, PARTNER).orElseThrow()
                .getDefaultAccountId()).isNull();
    }

    @Test
    void otherMemberSeesTheirOwnDefaultNotTheOwners() throws Exception {
        setDefault(cashId).andExpect(status().isOk());

        mockMvc.perform(put("/api/accounts/default").with(csrf()).with(user(PARTNER))
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": " + bankId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/accounts").with(user(PARTNER)).header("X-Ledger-Id", ledgerId))
                .andExpect(jsonPath("$.data[?(@.id == " + bankId + ")].default").value(true))
                .andExpect(jsonPath("$.data[?(@.id == " + cashId + ")].default").value(false));

        mockMvc.perform(get("/api/accounts").header("X-Ledger-Id", ledgerId))
                .andExpect(jsonPath("$.data[?(@.id == " + cashId + ")].default").value(true))
                .andExpect(jsonPath("$.data[?(@.id == " + bankId + ")].default").value(false));
    }

    @Test
    void settingDefaultAccountRejectsAccountFromAnotherLedger() throws Exception {
        Ledger other = ledgerRepository.save(new Ledger("别人的账本", PARTNER));
        ledgerMemberRepository.save(new LedgerMember(other.getId(), PARTNER, LedgerRole.OWNER));
        Long foreignId = accountRepository.save(accountInLedger("外币账户", other.getId())).getId();

        setDefault(foreignId).andExpect(status().isNotFound());
    }

    @Test
    void settingDefaultAccountRejectsInactiveAccount() throws Exception {
        Long inactiveId = accountRepository.save(account("已停用卡", false)).getId();

        setDefault(inactiveId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("停用")));
    }

    @Test
    void deactivatingTheDefaultAccountClearsThePreference() throws Exception {
        setDefault(cashId).andExpect(status().isOk());

        mockMvc.perform(put("/api/accounts/" + cashId).with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk());

        assertThat(ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, USERNAME).orElseThrow()
                .getDefaultAccountId()).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions setDefault(Long accountId) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("accountId", accountId);
        return mockMvc.perform(put("/api/accounts/default").with(csrf())
                .header("X-Ledger-Id", ledgerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Account account(String name, boolean active) {
        return accountInLedger(name, ledgerId, active);
    }

    private Account accountInLedger(String name, Long ledger) {
        return accountInLedger(name, ledger, true);
    }

    private Account accountInLedger(String name, Long ledger, boolean active) {
        Account account = new Account();
        account.setName(name);
        account.setType(AccountType.CASH);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setActive(active);
        account.setLedgerId(ledger);
        account.setCreatedBy(USERNAME);
        return account;
    }
}
