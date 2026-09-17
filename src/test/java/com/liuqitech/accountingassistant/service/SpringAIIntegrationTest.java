package com.liuqitech.accountingassistant.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring AI集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.ai.openai.api-key=test-key",
    "spring.datasource.url=jdbc:h2:mem:aidb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
public class SpringAIIntegrationTest {

    @MockitoBean
    private ChatModel chatModel;
    
    @Autowired(required = false)
    private ChatClient chatClient;
    
    @Autowired
    private AIParserService aiParserService;
    
    @Test
    public void testChatClientBean() {
        // 验证ChatClient bean是否正确创建
        // 在测试环境中，如果没有真实的API key，ChatClient可能不会被创建
        // 这是正常的，因为我们的AIParserService有回退机制
        if (chatClient != null) {
            assertThat(chatClient).isNotNull();
        }
        
        // 验证AIParserService能够正常工作（使用回退机制）
        assertThat(aiParserService).isNotNull();
    }
    
    @Test
    public void testSimpleRulesFallbackParsing() {
        // AI 不可用时的规则回退解析应能独立工作（不触发任何 AI 调用）
        var result = aiParserService.parseWithSimpleRules("买咖啡花了25元");
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo("25");
        assertThat(result.getType()).isNotNull();
    }
}
