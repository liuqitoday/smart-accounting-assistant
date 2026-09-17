package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.TransactionAndCategoryParseResult;
import com.liuqitech.accountingassistant.dto.TransactionParseResult;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.util.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI文本解析服务
 */
@Service
public class AIParserService {

    private static final Logger logger = LoggerFactory.getLogger(AIParserService.class);

    /**
     * 带单位锚点的金额模式：如 "35元"、"23.5块"、"88¥"。
     * 数字段前不能紧跟数字或小数点，避免 "3.5.6元" 之类畸形输入被截段误读。
     */
    private static final Pattern ANCHORED_AMOUNT =
            Pattern.compile("(?<![0-9.])([0-9]+(?:\\.[0-9]{1,2})?)\\s*[元块¥￥]");

    /** 独立数字串：前后都不是数字或小数点（同样排除多小数点畸形） */
    private static final Pattern STANDALONE_NUMBER =
            Pattern.compile("(?<![0-9.])[0-9]+(?:\\.[0-9]{1,2})?(?![0-9.])");

    private final ChatClient chatClient;

    @Autowired
    public AIParserService(ChatModel chatModel) {
        // 模型参数（model / temperature / top-p）统一在 application.yml
        // 的 spring.ai.openai.chat.options 中配置，这里不再硬编码。
        // 不挂 SimpleLoggerAdvisor：它会把完整 prompt（含用户记账原文）写入日志落盘，有隐私风险。
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 使用简单规则解析文本（AI 不可用时的 fallback；合并解析流程也复用此方法，
     * 避免合并调用失败后再次重试 AI）
     */
    TransactionParseResult parseWithSimpleRules(String text) {
        TransactionParseResult result = new TransactionParseResult();

        // 金额提取：优先"数字+元/块/¥"锚点；无锚点取最后一个独立数字串（记账口语金额通常在末尾）；
        // 都提不到则明确报错，绝不落 0 或把多个数字拼接成错误金额
        result.setAmount(extractAmount(text));

        // 简单的类型判断
        if (text.contains("收入") || text.contains("工资") || text.contains("奖金") || text.contains("退款")) {
            result.setType(TransactionType.INCOME);
        } else {
            result.setType(TransactionType.EXPENSE);
        }

        // 提取商家名称（简单逻辑）
        if (text.contains("星巴克")) {
            result.setMerchant("星巴克");
        } else if (text.contains("麦当劳")) {
            result.setMerchant("麦当劳");
        } else if (text.contains("超市")) {
            result.setMerchant("超市");
        } else {
            result.setMerchant("未知商家");
        }

        // 生成描述
        result.setDescription(text.length() > 20 ? text.substring(0, 20) + "..." : text);

        // 使用当前日期作为交易日期
        result.setTransactionDate(AppClock.today());

        // 新字段设为null（简单规则无法提取）
        result.setNote(null);
        result.setRelatedUser(null);

        // 设置较低的置信度，表明这是简单规则解析
        result.setConfidence(new BigDecimal("0.6"));

        return result;
    }

    /**
     * 规则解析的金额提取。锚点（元/块/¥）与独立数字都取最后一个匹配；
     * 完全提不到金额时抛业务异常，由上层以 400 告知用户补充金额。
     */
    private BigDecimal extractAmount(String text) {
        // 归一化千分位（"2,000元" → "2000元"），避免逗号把数字截成错误金额
        String normalized = text.replaceAll("(?<=[0-9]),(?=[0-9])", "");

        String candidate = null;
        Matcher anchored = ANCHORED_AMOUNT.matcher(normalized);
        while (anchored.find()) {
            candidate = anchored.group(1);
        }
        if (candidate == null) {
            Matcher standalone = STANDALONE_NUMBER.matcher(normalized);
            while (standalone.find()) {
                candidate = standalone.group();
            }
        }
        if (candidate == null) {
            throw new BusinessException("无法识别金额，请补充金额信息");
        }
        return new BigDecimal(candidate);
    }

    /**
     * 使用AI将提示词解析为指定的结构化类型
     *
     * @param prompt 提示词
     * @param type   目标类型
     * @return 解析后的对象
     */
    public <T> T parseStructured(String prompt, Class<T> type) {
        try {
            logger.debug("开始结构化解析，目标类型: {}", type.getSimpleName());

            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(type);

        } catch (Exception e) {
            logger.error("AI结构化解析失败", e);
            throw new RuntimeException("AI解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用AI生成纯文本回复（不做结构化解析），供分析问数的作答阶段使用
     */
    public String chatText(String prompt) {
        try {
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                throw new RuntimeException("AI服务返回空结果");
            }
            return content;
        } catch (Exception e) {
            logger.error("AI文本生成失败", e);
            throw new RuntimeException("AI文本生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 纯文本补全，system 与 user 分离。不记录 prompt 正文。
     */
    public String completeText(String system, String user) {
        try {
            String content = chatClient.prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                throw new RuntimeException("AI服务返回空结果");
            }
            return content;
        } catch (Exception e) {
            logger.error("AI文本生成失败", e);
            throw new RuntimeException("AI文本生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 一次 AI 调用同时完成交易抽取 + 分类选择，合并原先「抽取」与「分类」两次调用，
     * 省掉一次网络往返。分类树文本由调用方（CategoryService.getFullCategoryTreeText()）提供，
     * 本服务不依赖 CategoryService，避免循环依赖。
     *
     * @param text             原始交易文本
     * @param categoryTreeText 全类目树文本（支出 + 收入）
     * @return 合并解析结果（抽取字段 + 分类字段）
     */
    public TransactionAndCategoryParseResult parseTransactionAndCategory(String text, String categoryTreeText) {
        String promptText = buildCombinedPrompt(text, categoryTreeText);
        try {
            logger.debug("开始合并 AI 解析（抽取+分类）: {}", text);
            TransactionAndCategoryParseResult result = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .entity(TransactionAndCategoryParseResult.class);
            if (result == null) {
                throw new RuntimeException("AI服务返回空结果");
            }
            return result;
        } catch (Exception e) {
            logger.error("合并 AI 解析失败", e);
            throw new RuntimeException("AI合并解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建合并解析提示词（抽取规则 + 类目树 + 分类规则 + 合并 JSON schema）。
     */
    private String buildCombinedPrompt(String text, String categoryTreeText) {
        LocalDate today = AppClock.today();
        return String.format("""
                你是一个专业的财务记账助手，请分析以下交易描述，一次性完成两件事：① 提取交易关键字段；② 从分类树中选择最合适的分类。

                文本: %s
                当前日期: %s（%s），%d年

                %s

                请以 JSON 格式返回，包含以下字段：
                - amount: 金额（数字，保留2位小数）
                - type: 类型（INCOME 或 EXPENSE）
                - merchant: 商家/交易对方名称（文本中提到则提取，否则为 null）
                - description: 交易描述（简洁明了，10-20字）
                - transactionDate: 交易日期（YYYY-MM-DD；识别"今天/昨天/前天/上周X/12月15日"等相对或绝对日期，只有月日则用当前年份，无日期信息则用当前日期）
                - note: 备注（一句话说明钱怎么花的/来的，可含人物；无法提取才为 null）
                - relatedUser: 相关人员（人名或称呼，无则 null）
                - confidence: 抽取置信度（0-1，2位小数）
                - categoryId: 最终选择的分类ID（优先二级分类，无合适二级则返回一级ID）
                - categoryName: 最终选择的分类名称
                - parentCategoryId: 一级分类ID（选二级分类时必填其真实父ID；选一级分类时为 null）
                - parentCategoryName: 一级分类名称（选二级分类时必填；选一级分类时为 null）
                - categoryConfidence: 分类置信度（0-1；精确匹配0.9-1.0，模糊0.6-0.8，不确定0.3-0.5）
                - reason: 分类选择理由

                规则：
                1. type 判断：INCOME=收入/工资/奖金/退款/报销/转入/收到；EXPENSE=支出/购买/消费/付款/转出/花费。
                2. 分类层级必须正确：返回二级分类时，parentCategoryId/parentCategoryName 必须是其真实父分类；二级分类必须在对应一级分类下，不能随意组合。
                3. 优先选二级分类（更精确）；无法确定具体二级分类时只返回一级分类ID，此时 parentCategoryId/parentCategoryName 为 null。
                4. 选择的分类必须与 type 一致：type=EXPENSE 从【支出分类树】选，type=INCOME 从【收入分类树】选。
                5. transactionDate：相对日期按当前日期推算（今天=当前日期，昨天=-1天，前天=-2天，上周X=上一个周X，上个月X号=上个月X号）；绝对日期如"7月20日"默认当前年份；无日期信息用当前日期。
                6. 只返回 JSON，不要其他内容。

                示例：
                输入："今天在星巴克给女儿买了一杯咖啡，花了35元"
                输出：{"amount":35.00,"type":"EXPENSE","merchant":"星巴克","description":"购买咖啡","transactionDate":"%s","note":"给女儿买的咖啡","relatedUser":"女儿","confidence":0.95,"categoryId":123,"categoryName":"咖啡","parentCategoryId":100,"parentCategoryName":"餐饮","categoryConfidence":0.95,"reason":"明确提到星巴克和咖啡"}
                """, text, today.toString(), getDayOfWeekChinese(today), today.getYear(),
                categoryTreeText, today.toString());
    }

    private String getDayOfWeekChinese(LocalDate date) {
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return days[date.getDayOfWeek().getValue() - 1];
    }
}
