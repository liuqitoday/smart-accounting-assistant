package com.liuqitech.accountingassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Runs the real Spring AI serialization/deserialization against an in-process HTTP mock. */
class ImageTransactionOpenAiContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private MockRestServiceServer server;
    private ImageTransactionService service;
    private byte[] image;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder restClient = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClient).build();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("https://configured-provider.invalid")
                .apiKey("unit-test-key")
                .restClientBuilder(restClient)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("configured-vision-model")
                        .temperature(0.15).topP(0.7).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        CategoryService categories = mock(CategoryService.class);
        when(categories.getFullCategoryTreeText()).thenReturn("支出：餐饮");
        service = new ImageTransactionService(model, categories, mock(CategoryCorrectionService.class),
                mock(AccountRepository.class), true, "configured-vision-model");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedImage fixture = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        assertTrue(ImageIO.write(fixture, "png", bytes));
        fixture.flush();
        image = bytes.toByteArray();
    }

    @Test
    void postsTextAndBase64ImageUsingConfiguredUrlKeyAndModel() throws Exception {
        String modelJson = """
                {"transactions":[{"amount":18.50,"type":"EXPENSE","description":"午餐",
                "transactionDate":"2026-11-09","currency":"CNY","confidence":0.95}],"warnings":[]}
                """;
        String wireResponse = JSON.writeValueAsString(Map.of(
                "id", "test-completion", "object", "chat.completion", "model", "configured-vision-model",
                "choices", List.of(Map.of("index", 0, "message", Map.of("role", "assistant", "content", modelJson),
                        "finish_reason", "stop")),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 10, "total_tokens", 20)));
        server.expect(requestTo("https://configured-provider.invalid/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer unit-test-key"))
                .andExpect(request -> {
                    JsonNode body = JSON.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals("configured-vision-model", body.path("model").asText());
                    assertEquals(0.15, body.path("temperature").asDouble());
                    assertEquals(0.7, body.path("top_p").asDouble());
                    JsonNode user = null;
                    JsonNode system = null;
                    for (JsonNode message : body.path("messages")) {
                        if ("user".equals(message.path("role").asText())) user = message;
                        if ("system".equals(message.path("role").asText())) system = message;
                    }
                    assertNotNull(user);
                    assertNotNull(system);
                    assertFalse(system.path("content").asText().contains("支出：餐饮"));
                    boolean foundText = false;
                    boolean foundImage = false;
                    for (JsonNode part : user.path("content")) {
                        if ("text".equals(part.path("type").asText())) {
                            foundText = !part.path("text").asText().isBlank();
                            assertEquals("支出：餐饮", JSON.readTree(part.path("text").asText()).path("categoryTree").asText());
                        }
                        if ("image_url".equals(part.path("type").asText())) {
                            foundImage = true;
                            assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(image),
                                    part.path("image_url").path("url").asText());
                        }
                    }
                    assertTrue(foundText, "request must include the recognition instruction");
                    assertTrue(foundImage, "request must include actual screenshot media");
                })
                .andRespond(withSuccess(wireResponse, MediaType.APPLICATION_JSON));

        var draft = service.parseImage(service.validateImage(
                new MockMultipartFile("file", "receipt.png", "image/png", image)), 20L).transactions().get(0);
        assertEquals(new BigDecimal("18.50"), draft.getAmount());
        assertEquals("configured-vision-model", draft.getAiModelUsed());
        assertNull(draft.getId());
        server.verify();
    }

    @Test
    void unsupportedVisionProviderResponseBecomesUsefulErrorWithoutFakeResult() {
        server.expect(requestTo("https://configured-provider.invalid/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"provider-private-detail: images unsupported\"}}"));
        BusinessException error = assertThrows(BusinessException.class, () -> service.parseImage(
                new ImageTransactionService.ValidatedImage(image, "image/png"), 20L));
        assertEquals("IMAGE_PARSE_FAILED", error.getErrorCode());
        assertFalse(error.getMessage().contains("provider-private-detail"));
        server.verify();
    }
}
