package com.liuqitech.accountingassistant.service.analysis;

import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.TagRepository;
import com.liuqitech.accountingassistant.repository.projection.AnalysisNamedOption;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnalysisPromptBuilder {

    static final int MAX_ACCOUNTS = 30;
    static final int MAX_TAGS = 30;
    static final int MAX_CATEGORIES = 50;
    static final int MAX_OPTION_NAME_CHARS = 40;

    private final AccountRepository accountRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;

    public AnalysisPromptBuilder(AccountRepository accountRepository,
                                 TagRepository tagRepository,
                                 CategoryRepository categoryRepository) {
        this.accountRepository = accountRepository;
        this.tagRepository = tagRepository;
        this.categoryRepository = categoryRepository;
    }

    static final String PLANNER_RULES = """
        只输出分析计划 JSON，不要生成 SQL。
        所有枚举必须使用下面的大写英文值；禁止把 JSON Schema 里的类型名（如 string）当作取值。
        用不到的可选字段填 null，不要编造。

        operation: AGGREGATE, PERIOD_COMPARE, NET_CASH_FLOW, BREAKDOWN, TREND, AVERAGE_BY_PERIOD, TRANSACTIONS, COMPOSITE
        comparisonMode: 仅 PERIOD_COMPARE 时填写 PREVIOUS_PERIOD 或 SAME_PERIOD_LAST_YEAR，其他 operation 必须为 null
        queries[].kind: AGGREGATE, BREAKDOWN, TREND, TRANSACTIONS
        queries[].metric: SUM, COUNT, AVG
        queries[].transactionType: INCOME 或 EXPENSE；合计收支时为 null
        queries[].dimension: 仅 BREAKDOWN 填写 PARENT_CATEGORY, CHILD_CATEGORY, ACCOUNT, MERCHANT, TAG，其他为 null
        queries[].timeGrain: 仅 TREND 填写 DAY, WEEK, MONTH, QUARTER, YEAR，其他为 null
        queries[].sortField: AMOUNT 或 DATE，可 null
        queries[].sortDirection: ASC 或 DESC，可 null
        period.preset: CURRENT_DAY, CURRENT_WEEK, CURRENT_MONTH, CURRENT_QUARTER, CURRENT_YEAR, PREVIOUS_MONTH, PREVIOUS_YEAR, LAST_N_MONTHS, EXPLICIT_RANGE

        组合规则：
        - AGGREGATE/BREAKDOWN/TREND/TRANSACTIONS：恰好 1 个同 kind 查询
        - PERIOD_COMPARE：2 个仅周期不同的 AGGREGATE，且 comparisonMode 非空
        - NET_CASH_FLOW：1 个 INCOME AGGREGATE + 1 个 EXPENSE AGGREGATE
        - AVERAGE_BY_PERIOD：1 个 TREND
        - COMPOSITE：2-3 个基础查询并列

        示例（本月花了多少钱）：
        {"operation":"AGGREGATE","comparisonMode":null,"title":"本月支出总额","queries":[{"id":"q1","kind":"AGGREGATE","metric":"SUM","transactionType":"EXPENSE","dimension":null,"timeGrain":null,"sortField":null,"sortDirection":null,"limit":10,"title":"本月支出总额","period":{"preset":"CURRENT_MONTH","start":null,"end":null,"count":null},"filters":null}]}
        """;

    public String buildPlannerRules() {
        return PLANNER_RULES;
    }

    /**
     * AI① 轻量选项片段：账户/标签/分类均有数量上限，不加载消息 payload。
     */
    public String buildOptionSnippet(Long ledgerId) {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "可用账户",
            accountRepository.findAnalysisOptionsByLedgerId(ledgerId, PageRequest.of(0, MAX_ACCOUNTS)));
        appendSection(sb, "可用标签",
            tagRepository.findAnalysisOptions(ledgerId, PageRequest.of(0, MAX_TAGS)));
        appendSection(sb, "可用分类",
            categoryRepository.findAnalysisOptions(PageRequest.of(0, MAX_CATEGORIES)));
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String title, List<AnalysisNamedOption> options) {
        sb.append("## ").append(title).append('\n');
        for (AnalysisNamedOption option : options) {
            sb.append("- ID: ").append(option.getId())
                .append(", 名称: ").append(truncate(option.getName())).append('\n');
        }
        sb.append('\n');
    }

    private static String truncate(String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= MAX_OPTION_NAME_CHARS ? name : name.substring(0, MAX_OPTION_NAME_CHARS);
    }
}
