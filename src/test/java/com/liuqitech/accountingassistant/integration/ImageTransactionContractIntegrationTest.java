package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.LedgerRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.repository.UserRepository;
import com.liuqitech.accountingassistant.service.ImageTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:imagecontractdb",
        "spring.sql.init.mode=never",
        "app.ai.multimodal-enabled=true",
        "app.ai.daily-limit=1",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImageTransactionContractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private LedgerRepository ledgers;
    @Autowired private LedgerMemberRepository members;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private TransactionRepository transactions;
    @Autowired private ImageTransactionService imageService;
    @Autowired private CacheManager cacheManager;
    @MockitoBean private ChatModel chatModel;

    private String username;
    private Long ledgerId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        username = "image_" + UUID.randomUUID().toString().substring(0, 8);
        ledgerId = ledgers.save(new Ledger("截图测试账本", username)).getId();
        members.save(new LedgerMember(ledgerId, username, LedgerRole.OWNER));
        User owner = new User();
        owner.setUsername(username);
        owner.setPassword("unused");
        owner.setDefaultLedgerId(ledgerId);
        users.save(owner);
        Category category = new Category();
        category.setName("餐饮");
        category.setLevel(1);
        category.setType(TransactionType.EXPENSE);
        categoryId = categories.save(category).getId();
        cacheManager.getCache("categoryTree").clear();
    }

    @Test
    void capabilitiesExposeOnlyFlagAndLimits() throws Exception {
        mvc.perform(get("/api/transactions/capabilities").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageParsingEnabled").value(true))
                .andExpect(jsonPath("$.data.maxImageSizeBytes").value(5242880))
                .andExpect(jsonPath("$.data.maxTransactionsPerImage").value(20))
                .andExpect(jsonPath("$.data.supportedImageTypes[0]").value("image/png"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.baseUrl").doesNotExist());
    }

    @Test
    void recognitionOnlyReturnsDraftAndExplicitSaveCreatesTransaction() throws Exception {
        modelReturnsTransaction();
        long before = transactions.count();
        String response = mvc.perform(upload().file(png()).with(user(username)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions[0].amount").value(35.5))
                .andExpect(jsonPath("$.data.transactions[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$.data.transactions[0].id").isEmpty())
                .andExpect(jsonPath("$.data.warnings").isArray())
                .andReturn().getResponse().getContentAsString();
        assertThat(transactions.count()).isEqualTo(before);
        JsonNode draft = mapper.readTree(response).path("data").path("transactions").get(0);
        mvc.perform(post("/api/transactions/save").with(user(username)).with(csrf())
                        .header("X-Ledger-Id", ledgerId)
                        .contentType(MediaType.APPLICATION_JSON).content(draft.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber());
        assertThat(transactions.count()).isEqualTo(before + 1);
    }

    @Test
    void disabledFeatureRejectsDirectUploadWithoutConsumingQuota() throws Exception {
        ReflectionTestUtils.setField(imageService, "multimodalEnabled", false);
        try {
            mvc.perform(get("/api/transactions/capabilities").with(user(username)))
                    .andExpect(jsonPath("$.data.imageParsingEnabled").value(false));
            mvc.perform(upload().file(png()).with(user(username)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_PARSING_DISABLED"));
            verify(chatModel, never()).call(any(Prompt.class));
        } finally {
            ReflectionTestUtils.setField(imageService, "multimodalEnabled", true);
        }
        modelReturnsTransaction();
        mvc.perform(upload().file(png()).with(user(username)).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void invalidFileDoesNotConsumeQuotaAndTextAndImageShareQuota() throws Exception {
        mvc.perform(upload().file(new MockMultipartFile("file", "fake.png", "image/png", new byte[]{1, 2}))
                        .with(user(username)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_IMAGE"));
        modelReturnsTransaction();
        mvc.perform(upload().file(png()).with(user(username)).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/transactions/parse-only").with(user(username)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"咖啡35元\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("今日 AI 解析次数已达上限（1次），明日再来"));
    }

    @Test
    void unauthenticatedAndMissingCsrfCannotInvokeModel() throws Exception {
        mvc.perform(get("/api/transactions/capabilities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(upload().file(png()).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(upload().file(png()).with(user(username)))
                .andExpect(status().isForbidden());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void viewerAndForeignLedgerCannotRecognizeImage() throws Exception {
        Long foreign = ledgers.save(new Ledger("其他人的账本", "other")).getId();
        mvc.perform(multipart("/api/transactions/parse-image").file(png())
                        .header("X-Ledger-Id", foreign).with(user(username)).with(csrf()))
                .andExpect(status().isForbidden());
        Long viewed = ledgers.save(new Ledger("只读账本", "other")).getId();
        members.save(new LedgerMember(viewed, username, LedgerRole.VIEWER));
        mvc.perform(multipart("/api/transactions/parse-image").file(png())
                        .header("X-Ledger-Id", viewed).with(user(username)).with(csrf()))
                .andExpect(status().isForbidden());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void missingFileAndWrongContentTypeUseApiErrorEnvelope() throws Exception {
        mvc.perform(upload().with(user(username)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_UPLOAD"));
        mvc.perform(post("/api/transactions/parse-image").with(user(username)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void upgradedSpringdocGeneratesMultipartContract() throws Exception {
        mvc.perform(get("/v3/api-docs").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/transactions/parse-image'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.paths['/api/transactions/capabilities'].get").exists());
    }

    private MockMultipartHttpServletRequestBuilder upload() {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/transactions/parse-image");
        builder.header("X-Ledger-Id", ledgerId);
        return builder;
    }

    private MockMultipartFile png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "receipt.png", "image/png", output.toByteArray());
    }

    private void modelReturnsTransaction() throws Exception {
        String content = mapper.writeValueAsString(Map.of("transactions", List.of(Map.of(
                "amount", 35.50, "type", "EXPENSE", "merchant", "咖啡店", "description", "买咖啡",
                "originalText", "2026-11-10 咖啡店 实付35.50元", "transactionDate", "2026-11-10",
                "categoryId", categoryId, "currency", "CNY", "confidence", 0.98)), "warnings", List.of()));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
    }
}
