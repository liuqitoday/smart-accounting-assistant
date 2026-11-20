package com.liuqitech.accountingassistant.mcp;

import com.liuqitech.accountingassistant.dto.*;
import com.liuqitech.accountingassistant.dto.analysis.*;
import com.liuqitech.accountingassistant.enums.*;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.service.*;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPlanValidator;
import com.liuqitech.accountingassistant.service.analysis.AnalysisReadService;
import com.liuqitech.accountingassistant.util.AppClock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class McpAccountingFacade {
    private final McpConnectionService connections;
    private final LedgerService ledgers;
    private final CategoryService categories;
    private final AccountService accounts;
    private final TagService tags;
    private final TransactionSaveService saves;
    private final TransactionService transactions;
    private final TransactionRepository transactionRepository;
    private final AnalysisPlanValidator planValidator;
    private final AnalysisReadService analysis;
    private final TransactionTemplate reads;

    public McpAccountingFacade(McpConnectionService connections, LedgerService ledgers, CategoryService categories,
            AccountService accounts, TagService tags, TransactionSaveService saves, TransactionService transactions,
            TransactionRepository transactionRepository, AnalysisPlanValidator planValidator, AnalysisReadService analysis,
            PlatformTransactionManager transactionManager) {
        this.connections = connections;
        this.ledgers = ledgers;
        this.categories = categories;
        this.accounts = accounts;
        this.tags = tags;
        this.saves = saves;
        this.transactions = transactions;
        this.transactionRepository = transactionRepository;
        this.planValidator = planValidator;
        this.analysis = analysis;
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setReadOnly(true);
    }

    public Object execute(String tool, McpCaller caller, Map<String, Object> args) {
        if (tool.equals("accounting_create_transaction")) {
            Long ledgerId = number(args, "ledgerId");
            connections.authorize(caller, ledgerId, true);
            return create(caller, ledgerId, args);
        }
        // JPA option validation and JDBC statistics share one connection and one snapshot.
        // A write owns its separate receipt transaction and must not nest inside this read.
        return reads.execute(status -> read(tool, caller, args));
    }

    private Object read(String tool, McpCaller caller, Map<String, Object> args) {
        if (tool.equals("accounting_get_context")) return context(caller);
        Long ledgerId = number(args, "ledgerId");
        connections.authorize(caller, ledgerId, false);
        return switch (tool) {
            case "accounting_get_ledger_options" -> Map.of("ledgerId", ledgerId, "categories", categories.getAllCategories(),
                    "accounts", accounts.getAccountOptions(ledgerId, caller.username()), "tags", tags.getLedgerTags(ledgerId));
            case "accounting_list_transactions" -> list(ledgerId, args);
            case "accounting_get_transaction" -> detail(ledgerId, number(args, "transactionId"));
            case "accounting_get_statistics" -> statistics(ledgerId, args);
            default -> throw new BusinessException("工具不存在", "UNKNOWN_TOOL");
        };
    }

    private Object context(McpCaller caller) {
        var allowed = ledgers.listMyLedgers(caller.username()).stream().filter(l -> caller.ledgerIds().contains(l.getId())).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", AppClock.today());
        result.put("timezone", AppClock.ZONE.toString());
        result.put("currency", "CNY");
        result.put("scopes", caller.scopes());
        result.put("defaultLedgerId", allowed.stream().anyMatch(l -> l.getId().equals(caller.defaultLedgerId())) ? caller.defaultLedgerId() : null);
        result.put("ledgers", allowed.stream().map(l -> Map.of("id", l.getId(), "name", l.getName(), "role", l.getMyRole(),
                "canCreate", caller.writable() && l.getMyRole().atLeast(LedgerRole.EDITOR))).toList());
        return result;
    }

    private Object create(McpCaller caller, Long ledgerId, Map<String, Object> args) {
        SaveTransactionRequest request = new SaveTransactionRequest();
        request.setAmount(decimal(args, "amount"));
        if (request.getAmount().signum() <= 0) throw invalid("金额必须大于 0");
        request.setType(TransactionType.valueOf(text(args, "type")));
        request.setTransactionDate(date(args, "transactionDate"));
        if (request.getTransactionDate().isAfter(AppClock.today())) throw invalid("请仅记录已经发生的收支，日期不能在未来");
        request.setDescription(text(args, "description").trim());
        request.setOriginalText((String) args.getOrDefault("originalText", request.getDescription()));
        request.setCategoryId(number(args, "categoryId"));
        request.setAccountId(number(args, "accountId"));
        request.setTagIds(ids(args));
        request.setParsedMerchant(text(args, "merchant"));
        request.setNote(text(args, "note"));
        request.setRelatedUser(text(args, "relatedUser"));
        request.setClientRequestId(text(args, "clientRequestId"));
        request.setAiModelUsed("MCP");
        TransactionParseResponse saved = saves.save(request, ledgerId, caller.username(), () -> {
            // Validate again after claiming the writer lock. Replays need no new reference validation.
            McpCaller current = connections.refresh(caller);
            connections.authorize(current, ledgerId, true);
            if (request.getAccountId() != null && !accounts.requireAccountInLedger(ledgerId, request.getAccountId()).isActive()) {
                throw invalid("账户已停用，请选择启用账户");
            }
        }, response -> connections.record(caller, "accounting_create_transaction", ledgerId,
                request.getClientRequestId(), response.getId(), "CREATED", null, 0));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionId", saved.getId());
        result.put("ledgerId", ledgerId);
        result.put("currency", "CNY");
        result.put("amount", saved.getAmount());
        result.put("type", saved.getType());
        result.put("transactionDate", saved.getTransactionDate());
        result.put("description", saved.getDescription());
        result.put("categoryId", saved.getCategoryId());
        result.put("categoryName", saved.getCategoryName());
        result.put("accountId", saved.getAccountId());
        result.put("tags", saved.getTags());
        result.put("clientRequestId", request.getClientRequestId());
        return result;
    }

    private Object detail(Long ledgerId, Long id) {
        if (!transactionRepository.existsByIdAndLedgerIdAndDeletedAtIsNull(id, ledgerId)) {
            throw new ResourceNotFoundException("账目不存在");
        }
        return Map.of("ledgerId", ledgerId, "currency", "CNY", "transaction",
                transactions.getTransactionById(id, ledgerId).orElseThrow(() -> new ResourceNotFoundException("账目不存在")));
    }

    private Object list(Long ledgerId, Map<String, Object> args) {
        NormalizedAnalysisQuery validated = query(ledgerId, args, "AGGREGATE");
        int page = ((Number) args.getOrDefault("page", 0)).intValue();
        int size = ((Number) args.getOrDefault("pageSize", 20)).intValue();
        var result = transactions.queryAnalysisTransactions(ledgerId, validated,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate", "id")));
        return Map.of("ledgerId", ledgerId, "currency", "CNY", "items", result.getContent(),
                "page", page, "pageSize", size, "totalElements", result.getTotalElements(), "hasMore", !result.isLast(),
                "appliedFilters", applied(args));
    }

    private Object statistics(Long ledgerId, Map<String, Object> args) {
        String mode = text(args, "mode");
        if (!"trend".equals(mode) && args.containsKey("granularity")) throw invalid("granularity 仅适用于 trend");
        if ("by_category".equals(mode) && !args.containsKey("type")) throw invalid("分类统计请指定 INCOME 或 EXPENSE");
        String kind = switch (mode) { case "summary" -> "AGGREGATE"; case "by_category" -> "BREAKDOWN"; default -> "TREND"; };
        NormalizedAnalysisQuery query = query(ledgerId, args, kind);
        Object data;
        if (mode.equals("summary")) {
            var zero = new AnalysisAggregateFact(new BigDecimal("0.00"), 0);
            var income = query.transactionType() == TransactionType.EXPENSE ? zero : analysis.aggregate(ledgerId, withType(query, TransactionType.INCOME));
            var expense = query.transactionType() == TransactionType.INCOME ? zero : analysis.aggregate(ledgerId, withType(query, TransactionType.EXPENSE));
            data = Map.of("totalIncome", income.amount(), "totalExpense", expense.amount(), "netIncome", income.amount().subtract(expense.amount()),
                    "transactionCount", income.count() + expense.count());
        } else if (mode.equals("by_category")) {
            var breakdown = analysis.breakdown(ledgerId, query);
            data = Map.of("rows", breakdown.rows(), "totalAmount", breakdown.denominator(), "maxRows", 50,
                    "rowsMayBeTruncated", breakdown.rows().size() == 50);
        } else {
            data = analysis.trend(ledgerId, query);
        }
        return Map.of("ledgerId", ledgerId, "currency", "CNY", "timezone", AppClock.ZONE.toString(),
                "mode", mode, "appliedFilters", applied(args), "result", data);
    }

    private NormalizedAnalysisQuery query(Long ledgerId, Map<String, Object> args, String kind) {
        LocalDate start = date(args, "startDate"), end = date(args, "endDate");
        if (start.isAfter(end)) throw invalid("开始日期不能晚于结束日期");
        var period = new RawAnalysisPeriod();
        period.setPreset("EXPLICIT_RANGE"); period.setStart(start.toString()); period.setEnd(end.toString());
        var filters = new RawAnalysisFilters();
        filters.setCategoryId(number(args, "categoryId")); filters.setAccountId(number(args, "accountId"));
        filters.setTagIds(ids(args)); filters.setMerchant(text(args, "merchant")); filters.setKeyword(text(args, "keyword"));
        filters.setMinAmount(decimal(args, "minAmount")); filters.setMaxAmount(decimal(args, "maxAmount"));
        var query = new RawAnalysisQuery();
        query.setId("mcp"); query.setKind(kind); query.setMetric("SUM"); query.setPeriod(period); query.setFilters(filters);
        query.setTransactionType(text(args, "type")); query.setLimit(50);
        if (kind.equals("BREAKDOWN")) query.setDimension("PARENT_CATEGORY");
        if (kind.equals("TREND")) query.setTimeGrain(((String) args.getOrDefault("granularity", "month")).toUpperCase(Locale.ROOT));
        var plan = new RawAnalysisPlan(); plan.setOperation(kind); plan.setQueries(List.of(query));
        var validated = planValidator.validate(ledgerId, plan, AppClock.today());
        if (validated.normalizedPlan() == null) throw invalid(String.join("；", validated.errors()));
        return validated.normalizedPlan().queries().get(0);
    }

    private NormalizedAnalysisQuery withType(NormalizedAnalysisQuery q, TransactionType type) {
        return new NormalizedAnalysisQuery(q.id(), q.kind(), q.metric(), type, q.periodSemantic(), q.period(), q.filters(),
                q.dimension(), q.timeGrain(), q.sortField(), q.sortDirection(), q.limit(), q.title());
    }

    private Map<String, Object> applied(Map<String, Object> args) {
        Map<String, Object> result = new LinkedHashMap<>(args);
        result.keySet().removeAll(Set.of("ledgerId", "mode", "page", "pageSize"));
        if ("trend".equals(args.get("mode"))) result.putIfAbsent("granularity", "month");
        return result;
    }

    static Long number(Map<String, Object> args, String key) { return args.get(key) instanceof Number n ? n.longValue() : null; }
    static String text(Map<String, Object> args, String key) { return (String) args.get(key); }
    private BigDecimal decimal(Map<String, Object> args, String key) { return args.containsKey(key) ? new BigDecimal(text(args, key)) : null; }
    private List<Long> ids(Map<String, Object> args) { return ((List<?>) args.getOrDefault("tagIds", List.of())).stream().map(n -> ((Number) n).longValue()).distinct().toList(); }
    private LocalDate date(Map<String, Object> args, String key) {
        try {
            LocalDate date = LocalDate.parse(text(args, key));
            if (date.getYear() < 1) throw invalid("日期年份必须大于 0");
            return date;
        } catch (DateTimeParseException e) { throw invalid("日期不合法: " + key); }
    }
    private BusinessException invalid(String message) { return new BusinessException(message, "VALIDATION_ERROR"); }
}
