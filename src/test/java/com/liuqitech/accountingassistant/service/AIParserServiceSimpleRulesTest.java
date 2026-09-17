package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.TransactionParseResult;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.util.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 规则降级解析（parseWithSimpleRules）单元测试。
 * 重点覆盖金额提取：锚点匹配、末尾独立数字回退、无金额/畸形输入报错，
 * 防止历史「全文数字拼接」bug（"买了2杯咖啡35元" 被拼成 235）回归。
 */
class AIParserServiceSimpleRulesTest {

    private AIParserService aiParserService;

    @BeforeEach
    void setUp() {
        // parseWithSimpleRules 不触碰 AI，ChatModel 仅为满足构造器
        aiParserService = new AIParserService(Mockito.mock(ChatModel.class));
    }

    @Test
    void anchoredAmountWinsOverOtherDigits() {
        // 历史 bug：replaceAll 拼接出 235
        TransactionParseResult result = aiParserService.parseWithSimpleRules("买了2杯咖啡35元");
        assertEquals(0, result.getAmount().compareTo(new java.math.BigDecimal("35")));
    }

    @Test
    void trailingStandaloneNumberUsedWhenNoUnitAnchor() {
        TransactionParseResult result = aiParserService.parseWithSimpleRules("打车23.5");
        assertEquals(0, result.getAmount().compareTo(new java.math.BigDecimal("23.5")));
    }

    @Test
    void lastAnchoredAmountWinsWhenMultiple() {
        TransactionParseResult result = aiParserService.parseWithSimpleRules("早餐5元，午餐20元");
        assertEquals(0, result.getAmount().compareTo(new java.math.BigDecimal("20")));
    }

    @Test
    void supportsKuaiAndCurrencySignAnchors() {
        assertEquals(0, aiParserService.parseWithSimpleRules("奶茶12.5块")
                .getAmount().compareTo(new java.math.BigDecimal("12.5")));
        assertEquals(0, aiParserService.parseWithSimpleRules("超市购物88¥")
                .getAmount().compareTo(new java.math.BigDecimal("88")));
    }

    @Test
    void thousandsSeparatorIsNormalized() {
        TransactionParseResult result = aiParserService.parseWithSimpleRules("发工资2,000元");
        assertEquals(0, result.getAmount().compareTo(new java.math.BigDecimal("2000")));
    }

    @Test
    void textWithoutAnyNumberFailsInsteadOfZero() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiParserService.parseWithSimpleRules("买了一杯咖啡"));
        assertEquals("无法识别金额，请补充金额信息", exception.getMessage());
    }

    @Test
    void malformedMultiDotNumberFailsInsteadOfMisreading() {
        assertThrows(BusinessException.class,
                () -> aiParserService.parseWithSimpleRules("花了3.5.6元"));
    }

    @Test
    void incomeKeywordsProduceIncomeType() {
        TransactionParseResult result = aiParserService.parseWithSimpleRules("收到退款35元");
        assertEquals(TransactionType.INCOME, result.getType());
    }

    @Test
    void usesBusinessTimezoneToday() {
        TransactionParseResult result = aiParserService.parseWithSimpleRules("买咖啡35元");
        assertEquals(AppClock.today(), result.getTransactionDate());
    }
}
