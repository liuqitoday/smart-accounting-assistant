package com.liuqitech.accountingassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPlanValidationResult;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPeriod;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.RawAnalysisQuery;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.enums.AnalysisOperationKind;
import com.liuqitech.accountingassistant.enums.AnalysisPeriodPreset;
import com.liuqitech.accountingassistant.enums.AnalysisResponseStatus;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import com.liuqitech.accountingassistant.repository.TagRepository;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPeriodResolverImpl;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPlanValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AnalysisPlanValidatorTest {

    private static final Long ledgerId = 100L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private AnalysisPlanValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AnalysisPlanValidator(
            new AnalysisPeriodResolverImpl(),
            accountRepository,
            tagRepository,
            categoryRepository);

        lenient().when(accountRepository.existsByIdAndLedgerId(eq(9002L), eq(100L))).thenReturn(false);

        Tag otherLedgerTag = new Tag();
        otherLedgerTag.setId(7002L);
        otherLedgerTag.setSystem(false);
        otherLedgerTag.setLedgerId(999L);
        lenient().when(tagRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<Tag> found = new ArrayList<>();
            for (Long id : ids) {
                if (Long.valueOf(7002L).equals(id)) {
                    found.add(otherLedgerTag);
                }
            }
            return found;
        });
        lenient().when(tagRepository.findById(7002L)).thenReturn(Optional.of(otherLedgerTag));

        Category expense = new Category();
        expense.setId(1001L);
        expense.setType(TransactionType.EXPENSE);
        lenient().when(categoryRepository.findById(1001L)).thenReturn(Optional.of(expense));
    }

    @Test
    void unknownOperationIsBusinessValidationNotJacksonFailure() throws Exception {
        RawAnalysisPlan raw = objectMapper.readValue(
            "{\"operation\":\"NOT_A_KIND\",\"queries\":[]}", RawAnalysisPlan.class);

        AnalysisPlanValidationResult result = validator.validate(ledgerId, raw, LocalDate.of(2026, 8, 25));

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("operation"));
    }

    @Test
    void accountAndTagMustBelongToCurrentLedgerButGlobalCategoryIsAllowed() {
        // fixture 参数依次为 query kind、transaction type、跨账本 account/tagIds、全局 category。
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "EXPENSE", 9002L, List.of(7002L), 1001L);

        AnalysisPlanValidationResult result = validator.validate(100L, raw, LocalDate.of(2026, 8, 25));

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("accountId"));
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("tagIds"));
        assertThat(result.normalizedPlan()).isNull();
        assertThat(result.errors()).noneSatisfy(error -> assertThat(error).contains("categoryId"));
    }

    @Test
    void moreThanThreeQueriesAndMoreThanFiftyRowsAreRejectedInsteadOfTruncated() {
        RawAnalysisPlan raw = planWithQueryCount(4);
        raw.getQueries().get(0).setLimit(51);

        AnalysisPlanValidationResult result = validator.validate(100L, raw, LocalDate.of(2026, 8, 25));

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("最多 3 个基础查询"));
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("最多 50 行"));
        assertThat(raw.getQueries()).hasSize(4);
    }

    @Test
    void trendWithMoreThan366BucketsReturnsClarification() {
        RawAnalysisPlan raw = trendPlan("DAY", "2024-01-01", "2026-08-25");

        AnalysisPlanValidationResult result = validator.validate(100L, raw, LocalDate.of(2026, 8, 25));

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.CLARIFICATION_REQUIRED);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("366"));
    }

    @Test
    void rawPlanAndFreeTextBoundsGuaranteePayloadHeadroom() {
        RawAnalysisPlan raw = planWithQuery("TRANSACTIONS", "EXPENSE", null, List.of(), null);
        raw.getQueries().get(0).setTitle("x".repeat(121));
        raw.getQueries().get(0).getFilters().setKeyword("y".repeat(201));

        AnalysisPlanValidationResult result = validator.validate(100L, raw, LocalDate.of(2026, 8, 25));

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("title 最多 120 个字符"));
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("keyword 最多 200 个字符"));
    }

    @Test
    void wrongCategoryTypeIsRejected() {
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "INCOME", null, List.of(), 1001L);

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("categoryId"));
        assertThat(result.normalizedPlan()).isNull();
    }

    @Test
    void minAmountGreaterThanMaxAmountIsRejected() {
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null);
        raw.getQueries().get(0).getFilters().setMinAmount(new BigDecimal("100"));
        raw.getQueries().get(0).getFilters().setMaxAmount(new BigDecimal("10"));

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error ->
            assertThat(error).containsAnyOf("minAmount", "maxAmount"));
    }

    @Test
    void explicitDateStartAfterEndIsRejected() {
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null);
        RawAnalysisPeriod period = raw.getQueries().get(0).getPeriod();
        period.setPreset("EXPLICIT_RANGE");
        period.setStart("2026-08-25");
        period.setEnd("2026-08-01");

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error ->
            assertThat(error).containsAnyOf("start", "end", "日期"));
    }

    @Test
    void transactionsWithDimensionIsIllegal() {
        RawAnalysisPlan raw = planWithQuery("TRANSACTIONS", "EXPENSE", null, List.of(), null);
        raw.getQueries().get(0).setDimension("MERCHANT");

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error ->
            assertThat(error).containsAnyOf("dimension", "TRANSACTIONS"));
    }

    @Test
    void rawPlanSizeUnchangedAfterValidation() {
        RawAnalysisPlan raw = planWithQueryCount(4);
        List<RawAnalysisQuery> originalQueries = raw.getQueries();

        validator.validate(100L, raw, TODAY);

        assertThat(raw.getQueries()).isSameAs(originalQueries);
        assertThat(raw.getQueries()).hasSize(4);
    }

    @Test
    void lastNMonthsWithoutCountIsInvalidOrClarification() {
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null);
        raw.getQueries().get(0).getPeriod().setPreset("LAST_N_MONTHS");

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isIn(
            AnalysisResponseStatus.INVALID_PLAN, AnalysisResponseStatus.CLARIFICATION_REQUIRED);
        assertThat(result.normalizedPlan()).isNull();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void explicitRangeWithoutDatesIsInvalidOrClarification() {
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null);
        raw.getQueries().get(0).getPeriod().setPreset("EXPLICIT_RANGE");

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isIn(
            AnalysisResponseStatus.INVALID_PLAN, AnalysisResponseStatus.CLARIFICATION_REQUIRED);
        assertThat(result.normalizedPlan()).isNull();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void samePeriodLastYearPresetWithConflictingComparisonModeIsInvalid() {
        RawAnalysisPlan raw = planWithQuery("PERIOD_COMPARE", "EXPENSE", null, List.of(), null);
        raw.setOperation("PERIOD_COMPARE");
        raw.setComparisonMode("PREVIOUS_PERIOD");
        raw.getQueries().get(0).getPeriod().setPreset("SAME_PERIOD_LAST_YEAR");
        RawAnalysisQuery second = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null)
            .getQueries().get(0);
        second.setId("q2");
        second.getPeriod().setPreset("PREVIOUS_MONTH");
        raw.setQueries(new ArrayList<>(List.of(raw.getQueries().get(0), second)));

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("comparisonMode"));
    }

    @Test
    void transferTransactionTypeIsRejected() {
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "TRANSFER", null, List.of(), null);

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("transactionType"));
    }

    @Test
    void rawPlanOver32KiBStopsImmediately() {
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null);
        raw.setTitle("x".repeat(40_000));

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error ->
            assertThat(error).containsAnyOf("32", "KiB", "大小"));
        assertThat(result.errors()).noneSatisfy(error ->
            assertThat(error).contains("title 最多 120 个字符"));
    }

    @Test
    void nullQueriesListIsTreatedAsEmptyAndNotWrittenBack() {
        RawAnalysisPlan raw = new RawAnalysisPlan();
        raw.setOperation("AGGREGATE");
        raw.setQueries(null);

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isIn(
            AnalysisResponseStatus.INVALID_PLAN, AnalysisResponseStatus.CLARIFICATION_REQUIRED);
        assertThat(raw.getQueries()).isNull();
    }

    @Test
    void validAggregatePlanNormalizesWithoutMutatingRaw() {
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null);

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.OK);
        assertThat(result.errors()).isEmpty();
        assertThat(result.normalizedPlan()).isNotNull();
        assertThat(result.normalizedPlan().operation()).isEqualTo(AnalysisOperationKind.AGGREGATE);
        assertThat(result.normalizedPlan().comparisonMode()).isNull();
        assertThat(result.normalizedPlan().queries()).hasSize(1);
        NormalizedAnalysisQuery query = result.normalizedPlan().queries().get(0);
        assertThat(query.periodSemantic().preset()).isEqualTo(AnalysisPeriodPreset.CURRENT_MONTH);
        assertThat(query.period().start()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(query.period().end()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(raw.getQueries()).hasSize(1);
        assertThat(raw.getQueries().get(0).getTitle()).isEqualTo("测试查询");
    }

    @Test
    void schemaPlaceholdersAreTreatedAsMissingInsteadOfUnsupported() {
        RawAnalysisPlan raw = planWithQuery("string", "string", null, List.of(), null);
        raw.setOperation("string");
        raw.setComparisonMode("string");
        RawAnalysisQuery query = raw.getQueries().get(0);
        query.setKind("string");
        query.setMetric("string");
        query.setTransactionType("string");
        query.setDimension("string");
        query.getPeriod().setPreset("string");

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.CLARIFICATION_REQUIRED);
        assertThat(result.errors()).noneSatisfy(error -> assertThat(error).contains("值不受支持"));
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("operation 缺失"));
    }

    @Test
    void lowercaseEnumsAreNormalized() {
        RawAnalysisPlan raw = planWithQuery("aggregate", "expense", null, List.of(), null);
        raw.setOperation("aggregate");
        raw.getQueries().get(0).setMetric("sum");
        raw.getQueries().get(0).getPeriod().setPreset("current-month");

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.OK);
        assertThat(result.normalizedPlan().operation()).isEqualTo(AnalysisOperationKind.AGGREGATE);
        assertThat(result.normalizedPlan().queries().get(0).metric())
            .isEqualTo(com.liuqitech.accountingassistant.enums.AnalysisMetric.SUM);
        assertThat(result.normalizedPlan().queries().get(0).periodSemantic().preset())
            .isEqualTo(AnalysisPeriodPreset.CURRENT_MONTH);
    }

    @Test
    void queryIdAndMerchantBoundsAreEnforced() {
        RawAnalysisPlan raw = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null);
        raw.getQueries().get(0).setId("i".repeat(65));
        raw.getQueries().get(0).getFilters().setMerchant("m".repeat(201));
        raw.setTitle("t".repeat(121));

        AnalysisPlanValidationResult result = validator.validate(100L, raw, TODAY);

        assertThat(result.status()).isEqualTo(AnalysisResponseStatus.INVALID_PLAN);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("id 最多 64 个字符"));
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("merchant 最多 200 个字符"));
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("title 最多 120 个字符"));
    }

    private RawAnalysisPlan planWithQuery(String kind, String transactionType,
            Long accountId, List<Long> tagIds, Long categoryId) {
        RawAnalysisFilters filters = new RawAnalysisFilters();
        filters.setAccountId(accountId);
        filters.setTagIds(new ArrayList<>(tagIds));
        filters.setCategoryId(categoryId);
        RawAnalysisPeriod period = new RawAnalysisPeriod();
        period.setPreset("CURRENT_MONTH");
        RawAnalysisQuery query = new RawAnalysisQuery();
        query.setId("q1");
        query.setKind(kind);
        query.setMetric("SUM");
        query.setTransactionType(transactionType);
        query.setPeriod(period);
        query.setFilters(filters);
        query.setLimit(10);
        query.setTitle("测试查询");
        RawAnalysisPlan plan = new RawAnalysisPlan();
        plan.setOperation(kind);
        plan.setTitle("测试分析");
        plan.setQueries(new ArrayList<>(List.of(query)));
        return plan;
    }

    private RawAnalysisPlan planWithQueryCount(int count) {
        RawAnalysisPlan plan = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null);
        plan.setOperation("COMPOSITE");
        List<RawAnalysisQuery> queries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RawAnalysisQuery query = planWithQuery("AGGREGATE", "EXPENSE", null, List.of(), null)
                .getQueries().get(0);
            query.setId("q" + (i + 1));
            queries.add(query);
        }
        plan.setQueries(queries);
        return plan;
    }

    private RawAnalysisPlan trendPlan(String grain, String start, String end) {
        RawAnalysisPlan plan = planWithQuery("TREND", "EXPENSE", null, List.of(), null);
        RawAnalysisQuery query = plan.getQueries().get(0);
        query.setTimeGrain(grain);
        query.getPeriod().setPreset("EXPLICIT_RANGE");
        query.getPeriod().setStart(start);
        query.getPeriod().setEnd(end);
        return plan;
    }
}
