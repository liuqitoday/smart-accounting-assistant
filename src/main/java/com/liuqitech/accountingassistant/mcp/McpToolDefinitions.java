package com.liuqitech.accountingassistant.mcp;

import com.liuqitech.accountingassistant.exception.BusinessException;
import io.modelcontextprotocol.spec.McpSchema;

import java.math.BigDecimal;
import java.util.*;

/** Small, explicit schemas shared by discovery and runtime validation. No argument coercion. */
final class McpToolDefinitions {
    record Definition(String name, String description, Map<String, Object> properties, List<String> required) {
        McpSchema.Tool tool() {
            return McpSchema.Tool.builder().name(name).description(description)
                    .inputSchema(new McpSchema.JsonSchema("object", properties, required, false, null, null))
                    .annotations(new McpSchema.ToolAnnotations(null, !name.endsWith("create_transaction"), false, true, false, null))
                    .build();
        }

        void validate(Map<String, Object> args) {
            validateValue(Map.of("type", "object", "properties", properties, "required", required), args, "arguments");
        }
    }

    static List<Definition> all() {
        Map<String, Object> ledger = Map.of("ledgerId", id());
        var create = new LinkedHashMap<String, Object>(ledger);
        create.putAll(Map.of("amount", amount(), "currency", choices("CNY"), "type", choices("INCOME", "EXPENSE"),
                "transactionDate", date(), "description", text(500), "categoryId", id(), "accountId", id(),
                "tagIds", ids(), "originalText", text(2000), "merchant", text(200)));
        create.put("note", text(2000));
        create.put("relatedUser", text(100));
        create.put("clientRequestId", Map.of("type", "string", "pattern", "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                "description", "Stable UUID for one write intent. Reuse unchanged on every retry; a new transaction needs a new UUID."));
        var query = new LinkedHashMap<String, Object>(ledger);
        query.putAll(Map.of("startDate", date(), "endDate", date(), "type", choices("INCOME", "EXPENSE"),
                "categoryId", id(), "accountId", id(), "tagIds", ids(), "merchant", text(100),
                "keyword", text(100), "minAmount", amount(), "maxAmount", amount()));
        var list = new LinkedHashMap<>(query);
        query.put("keyword", Map.of("type", "string", "minLength", 1, "maxLength", 100,
                "description", "Literal substring of description, originalText or note. Use merchant to filter merchant names."));
        list.put("keyword", query.get("keyword"));
        list.put("page", integer(0, 10000));
        list.put("pageSize", integer(1, 100));
        var stats = new LinkedHashMap<>(query);
        stats.put("mode", choices("summary", "by_category", "trend"));
        stats.put("granularity", choices("day", "month"));
        return List.of(
                new Definition("accounting_get_context", "Get authorized ledgers, effective permissions, default ledger, CNY currency and today's date in Asia/Shanghai. Call before resolving relative dates or choosing a ledger.", Map.of(), List.of()),
                new Definition("accounting_get_ledger_options", "Get real category, account and tag IDs for one authorized ledger. Only active accounts can be used for new entries; omitted account means unspecified. Ask when names are ambiguous.", ledger, List.of("ledgerId")),
                new Definition("accounting_create_transaction", "Record ONE completed CNY income or expense after an explicit user request. Required category must come from options. No transfers or future plans. Return the saved receipt; never claim success before it arrives. Retry the exact same arguments and clientRequestId after a timeout. Do not generate another UUID for a retry.", create,
                        List.of("ledgerId", "amount", "currency", "type", "transactionDate", "description", "categoryId", "clientRequestId")),
                new Definition("accounting_list_transactions", "List transactions with inclusive startDate/endDate and filters; zero-based page defaults to 0, pageSize to 20 (max 100). Sorted by date then ID descending. Omitted type includes transfers. A page is not the full result; use statistics for totals.", list, List.of("ledgerId", "startDate", "endDate")),
                new Definition("accounting_get_transaction", "Get the current state of one transaction in the specified ledger. Deleted or inaccessible records return NOT_FOUND.", Map.of("ledgerId", id(), "transactionId", id()), List.of("ledgerId", "transactionId")),
                new Definition("accounting_get_statistics", "Compute exact server-side totals (summary), category breakdown (by_category), or trend. Dates are inclusive; transfers and deleted entries are excluded. Breakdown requires type; trend accepts granularity day/month (default month). Return actual filters and range; never silently drop a condition.", stats, List.of("ledgerId", "startDate", "endDate", "mode")));
    }

    private static Map<String, Object> text(int max) { return Map.of("type", "string", "minLength", 1, "maxLength", max); }
    private static Map<String, Object> choices(String... values) { return Map.of("type", "string", "enum", List.of(values)); }
    private static Map<String, Object> date() { return Map.of("type", "string", "pattern", "^\\d{4}-\\d{2}-\\d{2}$", "description", "YYYY-MM-DD, Asia/Shanghai business date"); }
    private static Map<String, Object> amount() { return Map.of("type", "string", "pattern", "^(0|[1-9][0-9]{0,12})(\\.[0-9]{1,2})?$", "description", "CNY decimal string, at most 13 integer digits and 2 decimal places; creation must be positive"); }
    private static Map<String, Object> integer(long min, long max) { return Map.of("type", "integer", "minimum", min, "maximum", max); }
    private static Map<String, Object> id() { return integer(1, 9007199254740991L); }
    private static Map<String, Object> ids() { return Map.of("type", "array", "items", id(), "maxItems", 100); }

    @SuppressWarnings("unchecked")
    private static void validateValue(Map<String, Object> schema, Object value, String path) {
        boolean valid = switch ((String) schema.get("type")) {
            case "string" -> value instanceof String s && !s.isBlank()
                    && s.length() >= ((Number) schema.getOrDefault("minLength", 0)).intValue()
                    && s.length() <= ((Number) schema.getOrDefault("maxLength", 3000)).intValue()
                    && (!schema.containsKey("pattern") || s.matches((String) schema.get("pattern")))
                    && (!schema.containsKey("enum") || ((List<?>) schema.get("enum")).contains(s));
            case "integer" -> value instanceof Number n && new BigDecimal(n.toString()).stripTrailingZeros().scale() <= 0
                    && new BigDecimal(n.toString()).compareTo(new BigDecimal(schema.get("minimum").toString())) >= 0
                    && new BigDecimal(n.toString()).compareTo(new BigDecimal(schema.get("maximum").toString())) <= 0;
            case "array" -> value instanceof List<?> l && l.size() <= ((Number) schema.get("maxItems")).intValue();
            case "object" -> value instanceof Map<?, ?>;
            default -> false;
        };
        if (!valid) throw new BusinessException("字段不合法: " + path, "VALIDATION_ERROR");
        if ("object".equals(schema.get("type"))) {
            Map<String, Object> values = (Map<String, Object>) value;
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            if (!props.keySet().containsAll(values.keySet())) throw new BusinessException("存在不支持的参数，请按工具 schema 调用", "VALIDATION_ERROR");
            for (String required : (List<String>) schema.get("required")) {
                if (!values.containsKey(required)) throw new BusinessException("缺少字段: " + required, "VALIDATION_ERROR");
            }
            values.forEach((key, item) -> validateValue((Map<String, Object>) props.get(key), item, key));
        } else if ("array".equals(schema.get("type"))) {
            for (Object item : (List<?>) value) validateValue((Map<String, Object>) schema.get("items"), item, path + "[]");
        }
    }
}
