package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:authdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class AuthSessionIntegrationTest {

    private static final String USERNAME = "session_user";
    private static final String PASSWORD = "session-pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User user = new User();
        user.setUsername(USERNAME);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setDefaultLedgerId(1L);
        userRepository.save(user);
    }

    @Test
    void meRequiresAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void loginCreatesSessionForSubsequentRequests() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(USERNAME))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(USERNAME));
    }

    private String loginJson() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(USERNAME);
        request.setPassword(PASSWORD);
        return objectMapper.writeValueAsString(request);
    }
}
