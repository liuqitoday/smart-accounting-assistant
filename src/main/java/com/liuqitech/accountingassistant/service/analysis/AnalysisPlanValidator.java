package com.liuqitech.accountingassistant.service.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPlanValidationResult;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPeriod;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisQuery;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.enums.AnalysisComparisonMode;
import com.liuqitech.accountingassistant.enums.AnalysisDimension;
import com.liuqitech.accountingassistant.enums.AnalysisMetric;
import com.liuqitech.accountingassistant.enums.AnalysisOperationKind;
import com.liuqitech.accountingassistant.enums.AnalysisPeriodPreset;
import com.liuqitech.accountingassistant.enums.AnalysisQueryKind;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.enums.AnalysisSortDirection;
import com.liuqitech.accountingassistant.enums.AnalysisSortField;
import com.liuqitech.accountingassistant.enums.AnalysisTimeGrain;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.TagRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class AnalysisPlanValidator {

    private static final int MAX_RAW_PLAN_BYTES = 32 * 1024;
    private static final int MAX_QUERIES = 3;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_TITLE_CHARS = 120;
    private static final int MAX_ID_CHARS = 64;
    private static final int MAX_TEXT_FILTER_CHARS = 200;
    private static final int MAX_TREND_BUCKETS = 366;
    private static final int DEFAULT_LIMIT = 10;
    private static final String SAME_PERIOD_LAST_YEAR = "SAME_PERIOD_LAST_YEAR";

    private final AnalysisPeriodResolver periodResolver;
    private final AccountRepository accountRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisPlanValidator(AnalysisPeriodResolver periodResolver,
                                 AccountRepository accountRepository,
                                 TagRepository tagRepository,
                                 CategoryRepository categoryRepository) {
        this.periodResolver = periodResolver;
        this.accountRepository = accountRepository;
        this.tagRepository = tagRepository;
        this.categoryRepository = categoryRepository;
    }

    public AnalysisPlanValidationResult validate(Long ledgerId, RawAnalysisPlan raw, LocalDate today) {
        AnalysisPlanValidationResult normalization = normalizeWithoutMutating(raw, today);
        if (normalization.status() != AnalysisResponseStatus.OK) {
            return normalization;
        }
        return validateNormalized(ledgerId, normalization.normalizedPlan());
    }

    private AnalysisPlanValidationResult normalizeWithoutMutating(RawAnalysisPlan raw, LocalDate today) {
        if (raw == null) {
            return AnalysisPlanValidationResult.invalid(List.of("计划不能为空"));
        }

        byte[] json;
        try {
            json = objectMapper.writeValueAsBytes(raw);
        } catch (JsonProcessingException e) {
            return AnalysisPlanValidationResult.invalid(List.of("原始计划无法序列化"));
        }
        if (json.length > MAX_RAW_PLAN_BYTES) {
            return AnalysisPlanValidationResult.invalid(List.of("原始计划超过 32 KiB"));
        }

        List<String> errors = new ArrayList<>();
        List<String> clarifications = new ArrayList<>();

        List<RawAnalysisQuery> rawQueries = raw.getQueries() == null ? List.of() : raw.getQueries();
        if (rawQueries.size() > MAX_QUERIES) {
            errors.add("最多 3 个基础查询");
        }

        checkCharLength(raw.getTitle(), MAX_TITLE_CHARS, "title 最多 120 个字符", errors);

        AnalysisOperationKind operation = parseEnum(
            AnalysisOperationKind.class, raw.getOperation(), "operation", errors);
        if (isBlank(raw.getOperation())) {
            clarifications.add("operation 缺失");
        }
        AnalysisComparisonMode comparisonMode = parseEnum(
            AnalysisComparisonMode.class, raw.getComparisonMode(), "comparisonMode", errors);

        List<DraftQuery> drafts = new ArrayList<>();
        for (int i = 0; i < rawQueries.size(); i++) {
            drafts.add(normalizeQuery(rawQueries.get(i), i, today, errors, clarifications));
        }

        boolean remappedSamePeriodLastYear = drafts.stream()
            .anyMatch(draft -> draft != null && draft.remappedSamePeriodLastYear);
        if (remappedSamePeriodLastYear) {
            if (comparisonMode == null && isBlank(raw.getComparisonMode())) {
                comparisonMode = AnalysisComparisonMode.SAME_PERIOD_LAST_YEAR;
            } else if (comparisonMode != null
                && comparisonMode != AnalysisComparisonMode.SAME_PERIOD_LAST_YEAR) {
                errors.add("字段 comparisonMode 与 period.preset=SAME_PERIOD_LAST_YEAR 冲突");
            }
        }

        validateCombinations(operation, comparisonMode, drafts, errors);

        if (!errors.isEmpty()) {
            return AnalysisPlanValidationResult.invalid(errors);
        }
        if (!clarifications.isEmpty()) {
            return AnalysisPlanValidationResult.clarification(clarifications);
        }

        List<NormalizedAnalysisQuery> queries = new ArrayList<>();
        for (DraftQuery draft : drafts) {
            queries.add(draft.toNormalized());
        }
        return AnalysisPlanValidationResult.valid(
            new NormalizedAnalysisPlan(operation, comparisonMode, queries, raw.getTitle()));
    }

    private DraftQuery normalizeQuery(RawAnalysisQuery rawQuery, int index, LocalDate today,
                                      List<String> errors, List<String> clarifications) {
        String path = "queries[" + index + "]";
        if (rawQuery == null) {
            errors.add(path + " 不能为空");
            return null;
        }

        DraftQuery draft = new DraftQuery();
        checkCharLength(rawQuery.getId(), MAX_ID_CHARS, path + ".id 最多 64 个字符", errors);
        checkCharLength(rawQuery.getTitle(), MAX_TITLE_CHARS, path + ".title 最多 120 个字符", errors);
        if (isBlank(rawQuery.getId())) {
            clarifications.add(path + ".id 缺失");
        }

        if (rawQuery.getLimit() != null && rawQuery.getLimit() > MAX_LIMIT) {
            errors.add(path + ".limit 最多 50 行");
        } else if (rawQuery.getLimit() != null && rawQuery.getLimit() < 1) {
            errors.add(path + ".limit 必须为正整数");
        }
        draft.id = rawQuery.getId();
        draft.title = rawQuery.getTitle();
        draft.limit = rawQuery.getLimit() == null ? DEFAULT_LIMIT : rawQuery.getLimit();

        draft.kind = parseEnum(AnalysisQueryKind.class, rawQuery.getKind(), path + ".kind", errors);
        if (isBlank(rawQuery.getKind())) {
            clarifications.add(path + ".kind 缺失");
        }
        draft.metric = parseEnum(AnalysisMetric.class, rawQuery.getMetric(), path + ".metric", errors);
        draft.transactionType = parseTransactionType(rawQuery.getTransactionType(), path, errors);
        draft.dimension = parseEnum(
            AnalysisDimension.class, rawQuery.getDimension(), path + ".dimension", errors);
        draft.timeGrain = parseEnum(
            AnalysisTimeGrain.class, rawQuery.getTimeGrain(), path + ".timeGrain", errors);
        draft.sortField = parseEnum(
            AnalysisSortField.class, rawQuery.getSortField(), path + ".sortField", errors);
        draft.sortDirection = parseEnum(
            AnalysisSortDirection.class, rawQuery.getSortDirection(), path + ".sortDirection", errors);

        if (draft.kind == AnalysisQueryKind.TRANSACTIONS && draft.dimension != null) {
            errors.add(path + ".dimension 不能与 TRANSACTIONS 同时使用");
        }
        if (draft.kind == AnalysisQueryKind.BREAKDOWN && draft.dimension == null
            && isBlank(rawQuery.getDimension())) {
            clarifications.add(path + ".dimension 缺失");
        }
        if (draft.kind == AnalysisQueryKind.TREND && draft.timeGrain == null
            && isBlank(rawQuery.getTimeGrain())) {
            clarifications.add(path + ".timeGrain 缺失");
        }

        draft.filters = normalizeFilters(rawQuery.getFilters(), path, errors);
        PeriodParse periodParse = parsePeriod(rawQuery.getPeriod(), path, today, errors, clarifications);
        draft.periodSemantic = periodParse.spec;
        draft.period = periodParse.range;
        draft.remappedSamePeriodLastYear = periodParse.remappedSamePeriodLastYear;

        if (draft.kind == AnalysisQueryKind.TREND && draft.timeGrain != null && draft.period != null) {
            int buckets = periodResolver.bucketCount(draft.period, draft.timeGrain);
            if (buckets > MAX_TREND_BUCKETS) {
                clarifications.add(path + " 趋势桶数超过 366");
            }
        }
        return draft;
    }

    private AnalysisFilters normalizeFilters(RawAnalysisFilters rawFilters, String path, List<String> errors) {
        if (rawFilters == null) {
            return AnalysisFilters.empty();
        }
        List<Long> tagIds = rawFilters.getTagIds() == null
            ? List.of() : List.copyOf(rawFilters.getTagIds());
        checkCharLength(rawFilters.getMerchant(), MAX_TEXT_FILTER_CHARS,
            path + ".filters.merchant 最多 200 个字符", errors);
        checkCharLength(rawFilters.getKeyword(), MAX_TEXT_FILTER_CHARS,
            path + ".filters.keyword 最多 200 个字符", errors);
        BigDecimal minAmount = rawFilters.getMinAmount();
        BigDecimal maxAmount = rawFilters.getMaxAmount();
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            errors.add(path + ".filters.minAmount 不能大于 maxAmount");
        }
        return new AnalysisFilters(
            rawFilters.getCategoryId(),
            rawFilters.getAccountId(),
            tagIds,
            rawFilters.getMerchant(),
            rawFilters.getKeyword(),
            minAmount,
            maxAmount);
    }

    private PeriodParse parsePeriod(RawAnalysisPeriod rawPeriod, String path, LocalDate today,
                                    List<String> errors, List<String> clarifications) {
        if (rawPeriod == null) {
            clarifications.add(path + ".period 缺失");
            return PeriodParse.missing();
        }
        String presetRaw = rawPeriod.getPreset();
        boolean remapped = presetRaw != null && SAME_PERIOD_LAST_YEAR.equalsIgnoreCase(presetRaw.trim());
        AnalysisPeriodPreset preset;
        if (remapped) {
            preset = inferCurrentRangePreset(rawPeriod);
        } else {
            preset = parseEnum(AnalysisPeriodPreset.class, presetRaw, path + ".period.preset", errors);
        }
        if (isBlank(presetRaw)) {
            clarifications.add(path + ".period.preset 缺失");
        }

        LocalDate start = parseDate(rawPeriod.getStart(), path + ".period.start", errors);
        LocalDate end = parseDate(rawPeriod.getEnd(), path + ".period.end", errors);
        Integer count = rawPeriod.getCount();

        if (preset == AnalysisPeriodPreset.LAST_N_MONTHS) {
            if (count == null || count < 1) {
                clarifications.add(path + ".period.count 缺失");
                return new PeriodParse(null, null, remapped);
            }
            AnalysisPeriodSpec spec = AnalysisPeriodSpec.lastNMonths(count);
            return new PeriodParse(spec, periodResolver.resolve(spec, today), remapped);
        }
        if (preset == AnalysisPeriodPreset.EXPLICIT_RANGE) {
            if (start == null || end == null) {
                clarifications.add(path + ".period.start/end 缺失");
                return new PeriodParse(null, null, remapped);
            }
            if (start.isAfter(end)) {
                errors.add(path + ".period.start 不能晚于 end");
                return new PeriodParse(null, null, remapped);
            }
            AnalysisPeriodSpec spec = AnalysisPeriodSpec.explicit(start, end);
            return new PeriodParse(spec, periodResolver.resolve(spec, today), remapped);
        }
        if (preset == null) {
            return new PeriodParse(null, null, remapped);
        }
        AnalysisPeriodSpec spec = AnalysisPeriodSpec.of(preset);
        return new PeriodParse(spec, periodResolver.resolve(spec, today), remapped);
    }

    private static AnalysisPeriodPreset inferCurrentRangePreset(RawAnalysisPeriod rawPeriod) {
        if (!isBlank(rawPeriod.getStart()) && !isBlank(rawPeriod.getEnd())) {
            return AnalysisPeriodPreset.EXPLICIT_RANGE;
        }
        if (rawPeriod.getCount() != null) {
            return AnalysisPeriodPreset.LAST_N_MONTHS;
        }
        return AnalysisPeriodPreset.CURRENT_MONTH;
    }

    private void validateCombinations(AnalysisOperationKind operation,
                                      AnalysisComparisonMode comparisonMode,
                                      List<DraftQuery> drafts,
                                      List<String> errors) {
        if (operation == null) {
            return;
        }
        List<DraftQuery> present = drafts.stream().filter(Objects::nonNull).toList();
        boolean comparisonAllowed = operation == AnalysisOperationKind.PERIOD_COMPARE;
        if (!comparisonAllowed && comparisonMode != null) {
            errors.add("字段 comparisonMode 仅 PERIOD_COMPARE 允许非空");
        }
        if (operation == AnalysisOperationKind.PERIOD_COMPARE && comparisonMode == null) {
            errors.add("字段 comparisonMode 不能为空");
        }

        switch (operation) {
            case AGGREGATE, BREAKDOWN, TREND, TRANSACTIONS -> {
                AnalysisQueryKind expected = AnalysisQueryKind.valueOf(operation.name());
                if (present.size() != 1 || present.get(0).kind != expected) {
                    errors.add("operation " + operation + " 要求恰好 1 个 " + expected + " 查询");
                }
            }
            case PERIOD_COMPARE -> {
                if (present.size() != 2
                    || present.get(0).kind != AnalysisQueryKind.AGGREGATE
                    || present.get(1).kind != AnalysisQueryKind.AGGREGATE) {
                    errors.add("PERIOD_COMPARE 要求两个 aggregate 查询");
                } else if (!sameCaliberExceptPeriod(present.get(0), present.get(1))) {
                    errors.add("PERIOD_COMPARE 的两个查询必须仅周期不同");
                }
            }
            case NET_CASH_FLOW -> {
                if (present.size() != 2
                    || present.get(0).kind != AnalysisQueryKind.AGGREGATE
                    || present.get(1).kind != AnalysisQueryKind.AGGREGATE) {
                    errors.add("NET_CASH_FLOW 要求一条 INCOME 和一条 EXPENSE aggregate 查询");
                } else {
                    Set<TransactionType> types = new HashSet<>();
                    types.add(present.get(0).transactionType);
                    types.add(present.get(1).transactionType);
                    if (!types.equals(Set.of(TransactionType.INCOME, TransactionType.EXPENSE))) {
                        errors.add("NET_CASH_FLOW 要求一条 INCOME 和一条 EXPENSE aggregate 查询");
                    }
                }
            }
            case AVERAGE_BY_PERIOD -> {
                if (present.size() != 1 || present.get(0).kind != AnalysisQueryKind.TREND) {
                    errors.add("AVERAGE_BY_PERIOD 要求一条 trend 查询");
                }
            }
            case COMPOSITE -> {
                if (present.size() < 2 || present.size() > MAX_QUERIES) {
                    errors.add("COMPOSITE 要求 2-3 个基础查询");
                }
            }
        }
    }

    private static boolean sameCaliberExceptPeriod(DraftQuery left, DraftQuery right) {
        return left.kind == right.kind
            && left.metric == right.metric
            && left.transactionType == right.transactionType
            && left.dimension == right.dimension
            && left.timeGrain == right.timeGrain
            && left.sortField == right.sortField
            && left.sortDirection == right.sortDirection
            && left.limit == right.limit
            && Objects.equals(left.filters, right.filters);
    }

    private AnalysisPlanValidationResult validateNormalized(Long ledgerId, NormalizedAnalysisPlan plan) {
        List<String> errors = new ArrayList<>();
        List<NormalizedAnalysisQuery> queries = plan.queries();
        for (int i = 0; i < queries.size(); i++) {
            NormalizedAnalysisQuery query = queries.get(i);
            String path = "queries[" + i + "].filters";
            AnalysisFilters filters = query.filters();
            if (filters.accountId() != null
                && !accountRepository.existsByIdAndLedgerId(filters.accountId(), ledgerId)) {
                errors.add(path + ".accountId 不属于当前账本");
            }
            validateTags(filters.tagIds(), ledgerId, path, errors);
            if (filters.categoryId() != null) {
                Category category = categoryRepository.findById(filters.categoryId()).orElse(null);
                if (category == null) {
                    errors.add(path + ".categoryId 不存在");
                } else if (query.transactionType() != null
                    && category.getType() != query.transactionType()) {
                    errors.add(path + ".categoryId 与交易类型不匹配");
                }
            }
        }
        if (!errors.isEmpty()) {
            return AnalysisPlanValidationResult.invalid(errors);
        }
        return AnalysisPlanValidationResult.valid(plan);
    }

    private void validateTags(List<Long> tagIds, Long ledgerId, String path, List<String> errors) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Map<Long, Tag> found = new LinkedHashMap<>();
        for (Tag tag : tagRepository.findAllById(tagIds)) {
            found.put(tag.getId(), tag);
        }
        for (Long tagId : tagIds) {
            Tag tag = found.get(tagId);
            if (tag == null || !(tag.isSystem() || Objects.equals(tag.getLedgerId(), ledgerId))) {
                errors.add(path + ".tagIds 不属于当前账本");
                return;
            }
        }
    }

    private TransactionType parseTransactionType(String raw, String path, List<String> errors) {
        TransactionType type = parseEnum(
            TransactionType.class, raw, path + ".transactionType", errors);
        if (type == TransactionType.TRANSFER) {
            errors.add(path + ".transactionType 只允许 INCOME/EXPENSE");
            return null;
        }
        return type;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field, List<String> errors) {
        if (isBlank(raw) || isSchemaPlaceholder(raw)) {
            return null;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Enum.valueOf(type, token);
        } catch (IllegalArgumentException ex) {
            errors.add("字段 " + field + " 的值不受支持");
            return null;
        }
    }

    private static boolean isSchemaPlaceholder(String raw) {
        String token = raw.trim();
        return token.equalsIgnoreCase("string")
            || token.equalsIgnoreCase("null")
            || token.equalsIgnoreCase("undefined")
            || token.equalsIgnoreCase("none")
            || token.equalsIgnoreCase("n/a")
            || token.equalsIgnoreCase("na");
    }

    private LocalDate parseDate(String raw, String field, List<String> errors) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            errors.add("字段 " + field + " 的值不受支持");
            return null;
        }
    }

    private static void checkCharLength(String value, int max, String message, List<String> errors) {
        if (value != null && value.length() > max) {
            errors.add(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || isSchemaPlaceholder(value);
    }

    private static final class DraftQuery {
        private String id;
        private AnalysisQueryKind kind;
        private AnalysisMetric metric;
        private TransactionType transactionType;
        private AnalysisPeriodSpec periodSemantic;
        private AnalysisDateRange period;
        private AnalysisFilters filters = AnalysisFilters.empty();
        private AnalysisDimension dimension;
        private AnalysisTimeGrain timeGrain;
        private AnalysisSortField sortField;
        private AnalysisSortDirection sortDirection;
        private int limit = DEFAULT_LIMIT;
        private String title;
        private boolean remappedSamePeriodLastYear;

        private NormalizedAnalysisQuery toNormalized() {
            return new NormalizedAnalysisQuery(
                id, kind, metric, transactionType, periodSemantic, period, filters,
                dimension, timeGrain, sortField, sortDirection, limit, title);
        }
    }

    private record PeriodParse(AnalysisPeriodSpec spec, AnalysisDateRange range,
                               boolean remappedSamePeriodLastYear) {
        private static PeriodParse missing() {
            return new PeriodParse(null, null, false);
        }
    }
}
