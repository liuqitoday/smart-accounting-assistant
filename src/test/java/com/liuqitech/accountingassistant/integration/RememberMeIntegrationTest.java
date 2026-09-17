package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.ChangePasswordRequest;
import com.liuqitech.accountingassistant.dto.LoginRequest;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Remember-Me 自动续登端到端行为：下发、落库、无 session 续登轮换、
 * session 接管、宽限重放、伪造 token 全作废。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:remembermedb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class RememberMeIntegrationTest {

    private static final String USERNAME = "rm_user";
    private static final String PASSWORD = "rm-pass";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Cookie csrfCookie;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM persistent_logins");
        userRepository.deleteAll();

        User user = new User();
        user.setUsername(USERNAME);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setDefaultLedgerId(1L);
        userRepository.save(user);
    }

    @Test
    void loginIssuesRememberMeCookieAndPersistsToken() throws Exception {
        MvcResult login = login();

        Cookie rememberMe = login.getResponse().getCookie("remember-me");
        assertNotNull(rememberMe);
        assertEquals(1, countTokens());
    }

    @Test
    void autoLoginWithoutSessionRotatesTokenAndEstablishesSession() throws Exception {
        Cookie rememberMe = login().getResponse().getCookie("remember-me");

        // 不带 session，仅凭 remember-me cookie 访问
        MvcResult me = mockMvc.perform(get("/api/auth/me").cookie(rememberMe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(USERNAME))
                .andReturn();

        // cookie 已轮换（滑动续期）
        Cookie rotated = me.getResponse().getCookie("remember-me");
        assertNotNull(rotated);
        assertNotEquals(rememberMe.getValue(), rotated.getValue());

        // 自动续登建立了新 session，后续请求仅凭 session 即可（不再触碰令牌）
        // 该断言依赖 Spring Security 6.1+ 将共享 SecurityContextRepository 注入
        // RememberMeAuthenticationFilter（Boot 3.5.4 → Security 6.5 满足）
        MockHttpSession session = (MockHttpSession) me.getRequest().getSession(false);
        assertNotNull(session);
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentReplayWithinGraceWindowIsAccepted() throws Exception {
        Cookie rememberMe = login().getResponse().getCookie("remember-me");

        MvcResult first = mockMvc.perform(get("/api/auth/me").cookie(rememberMe))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotated = first.getResponse().getCookie("remember-me");

        // 模拟并发：携带同一旧 cookie 再次请求 → 宽限放行，且下发同一个已轮换 token
        MvcResult replay = mockMvc.perform(get("/api/auth/me").cookie(rememberMe))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(rotated.getValue(), replay.getResponse().getCookie("remember-me").getValue());

        assertEquals(1, countTokens()); // 令牌未被作废
    }

    @Test
    void forgedTokenReturns401AndRevokesAllTokens() throws Exception {
        Cookie rememberMe = login().getResponse().getCookie("remember-me");
        String series = decodeCookie(rememberMe.getValue())[0];
        Cookie forged = new Cookie("remember-me", encodeCookie(series, "forged-token"));

        mockMvc.perform(get("/api/auth/me").cookie(forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        assertEquals(0, countTokens()); // 全部作废
    }

    @Test
    void logoutRevokesAllTokensAndExpiresCookie() throws Exception {
        MvcResult login = login();
        Cookie rememberMe = login.getResponse().getCookie("remember-me");
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(csrfCookie, rememberMe)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cancelled = logout.getResponse().getCookie("remember-me");
        assertNotNull(cancelled);
        assertEquals(0, cancelled.getMaxAge()); // cookie 立即过期
        assertEquals(0, countTokens());         // 全设备令牌作废
    }

    @Test
    void changePasswordRevokesOtherDevicesAndReissuesCurrent() throws Exception {
        login();                    // 设备 A
        MvcResult loginB = login(); // 设备 B（第二个 series）
        assertEquals(2, countTokens());
        Cookie deviceB = loginB.getResponse().getCookie("remember-me");
        MockHttpSession sessionB = (MockHttpSession) loginB.getRequest().getSession(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(PASSWORD);
        request.setNewPassword("new-pass-123");

        MvcResult change = mockMvc.perform(put("/api/auth/password")
                        .session(sessionB)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // 只剩当前设备的新令牌；其他设备（A）已被踢
        assertEquals(1, countTokens());
        Cookie reissued = change.getResponse().getCookie("remember-me");
        assertNotNull(reissued);
        assertNotEquals(deviceB.getValue(), reissued.getValue());
    }

    @Test
    void changePasswordEvictsOtherDevicesSessions() throws Exception {
        MvcResult loginA = login();
        MockHttpSession sessionA = (MockHttpSession) loginA.getRequest().getSession(false);
        MvcResult loginB = login();
        MockHttpSession sessionB = (MockHttpSession) loginB.getRequest().getSession(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(PASSWORD);
        request.setNewPassword("new-pass-123");
        mockMvc.perform(put("/api/auth/password")
                        .session(sessionB)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 其他设备（A）的活跃会话被吊销；当前设备（B）保持登录
        mockMvc.perform(get("/api/auth/me").session(sessionA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").session(sessionB))
                .andExpect(status().isOk());
    }

    @Test
    void logoutEvictsOtherDevicesSessions() throws Exception {
        MvcResult loginA = login();
        MockHttpSession sessionA = (MockHttpSession) loginA.getRequest().getSession(false);
        MvcResult loginB = login();
        MockHttpSession sessionB = (MockHttpSession) loginB.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout")
                        .session(sessionB)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(sessionA))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──

    /** 走 CSRF → 登录全流程，副作用：刷新 this.csrfCookie 供后续写请求使用。 */
    private MvcResult login() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        LoginRequest request = new LoginRequest();
        request.setUsername(USERNAME);
        request.setPassword(PASSWORD);
        return mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private int countTokens() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM persistent_logins WHERE username = ?", Integer.class, USERNAME);
        return count == null ? 0 : count;
    }

    /** 框架 cookie 格式：Base64("series:token")，编码时去 padding，解码前补齐。 */
    private String[] decodeCookie(String cookieValue) {
        String padded = cookieValue + "=".repeat((4 - cookieValue.length() % 4) % 4);
        return new String(Base64.getDecoder().decode(padded)).split(":");
    }

    private String encodeCookie(String series, String token) {
        return Base64.getEncoder().encodeToString((series + ":" + token).getBytes());
    }
}
