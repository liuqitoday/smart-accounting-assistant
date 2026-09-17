package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisDateRange;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisExecutionOutcome;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisFilters;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisPeriodSpec;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisResult;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTransactionFact;
import com.liuqitech.accountingassistant.dto.analysis.AnalysisTrendFact;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisPlan;
import com.liuqitech.accountingassistant.dto.analysis.NormalizedAnalysisQuery;
import com.liuqitech.accountingassistant.dto.analysis.result.AggregateResult;
import com.liuqitech.accountingassistant.dto.analysis.result.AverageByPeriodResult;
import com.liuqitech.accountingassistant.dto.analysis.result.BreakdownResult;
import com.liuqitech.accountingassistant.dto.analysis.result.TransactionsResult;
import com.liuqitech.accountingassistant.dto.analysis.result.TrendResult;
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
import com.liuqitech.accountingassistant.service.analysis.AnalysisOperationExecutor;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPeriodResolver;
import com.liuqitech.accountingassistant.service.analysis.AnalysisPeriodResolverImpl;
import com.liuqitech.accountingassistant.service.analysis.AnalysisReadService;
import com.liuqitech.accountingassistant.service.analysis.DerivedMetricCalculator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class AnalysisGoldenSetTest {

    private static final String JDBC_URL = "jdbc:sqlite:file:analysis-golden-test?mode=memory&cache=shared";
    private static final long LEDGER_ID = 100L;

    private static Connection keepAlive;
    private static AnalysisOperationExecutor executor;
    private static AnalysisPeriodResolver periodResolver;
    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private List<GoldenCase> cases;
    private GoldenFixturePlanner fixturePlanner;
    private final long ledgerId = LEDGER_ID;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(JDBC_URL);
        keepAlive = dataSource.getConnection();
        ScriptUtils.executeSqlScript(keepAlive, new ClassPathResource("analysis/sqlite-fixture.sql"));
        periodResolver = new AnalysisPeriodResolverImpl();
        executor = new AnalysisOperationExecutor(
            new AnalysisReadService(new JdbcTemplate(dataSource), periodResolver),
            periodResolver,
            new DerivedMetricCalculator());
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    @BeforeEach
    void loadCases() throws Exception {
        cases = objectMapper.readValue(
            new ClassPathResource("analysis/golden-set.json").getInputStream(),
            new TypeReference<List<GoldenCase>>() {});
        fixturePlanner = new JsonGoldenFixturePlanner(cases);
    }

    @Test
    void goldenSetHasNoCrossLedgerOrDateBoundaryRegression() throws Exception {
        assertThat(cases).hasSizeGreaterThanOrEqualTo(20);
        for (GoldenCase c : cases) {
            NormalizedAnalysisPlan plan = fixturePlanner.plan(c.question(), c.turns());
            assertExpectedPlan(c, plan);
            AnalysisExecutionOutcome outcome = executor.execute(
                ledgerId, plan, LocalDate.of(2026, 3, 31));
            assertThat(outcome.status())
                .as("case %s", c.id())
                .isEqualTo(c.expectedStatus());
            assertInvariants(c, plan, outcome.results());
        }
    }

    private void assertExpectedPlan(GoldenCase testCase, NormalizedAnalysisPlan plan) {
        JsonNode expected = testCase.expectedPlan();
        if (expected.has("operation")) {
            assertThat(plan.operation().name()).isEqualTo(expected.path("operation").asText());
        }
        if (expected.has("comparisonMode")) {
            assertThat(plan.comparisonMode()).isNotNull();
            assertThat(plan.comparisonMode().name()).isEqualTo(expected.path("comparisonMode").asText());
        }
        if (expected.has("queryCount")) {
            assertThat(plan.queries()).hasSize(expected.path("queryCount").asInt());
        }
        JsonNode expectedQueries = expected.path("queries");
        if (expectedQueries.isArray()) {
            assertThat(plan.queries()).hasSize(expectedQueries.size());
            for (int i = 0; i < expectedQueries.size(); i++) {
                JsonNode expectedQuery = expectedQueries.get(i);
                NormalizedAnalysisQuery actualQuery = plan.queries().get(i);
                if (expectedQuery.has("kind")) {
                    assertThat(actualQuery.kind().name()).isEqualTo(expectedQuery.path("kind").asText());
                }
                if (expectedQuery.has("metric")) {
                    assertThat(actualQuery.metric().name()).isEqualTo(expectedQuery.path("metric").asText());
                }
                if (expectedQuery.has("transactionType")) {
                    String expectedType = expectedQuery.path("transactionType").isNull()
                        ? null : expectedQuery.path("transactionType").asText();
                    assertThat(actualQuery.transactionType() == null ? null
                        : actualQuery.transactionType().name()).isEqualTo(expectedType);
                }
                if (expectedQuery.has("dimension")) {
                    assertThat(actualQuery.dimension().name()).isEqualTo(expectedQuery.path("dimension").asText());
                }
                if (expectedQuery.has("timeGrain")) {
                    assertThat(actualQuery.timeGrain().name()).isEqualTo(expectedQuery.path("timeGrain").asText());
                }
                if (expectedQuery.has("periodStart")) {
                    assertThat(actualQuery.period().start().toString())
                        .isEqualTo(expectedQuery.path("periodStart").asText());
                }
                if (expectedQuery.has("periodEnd")) {
                    assertThat(actualQuery.period().end().toString())
                        .isEqualTo(expectedQuery.path("periodEnd").asText());
                }
                JsonNode expectedFilters = expectedQuery.path("filters");
                if (expectedFilters.has("categoryId")) {
                    assertThat(actualQuery.filters().categoryId())
                        .isEqualTo(expectedFilters.path("categoryId").isNull()
                            ? null : expectedFilters.path("categoryId").asLong());
                }
                if (expectedFilters.has("accountId")) {
                    assertThat(actualQuery.filters().accountId())
                        .isEqualTo(expectedFilters.path("accountId").isNull()
                            ? null : expectedFilters.path("accountId").asLong());
                }
                if (expectedFilters.has("tagIds")) {
                    List<Long> expectedTagIds = new ArrayList<>();
                    expectedFilters.path("tagIds").forEach(node -> expectedTagIds.add(node.asLong()));
                    assertThat(actualQuery.filters().tagIds()).containsExactlyElementsOf(expectedTagIds);
                }
                if (expectedFilters.has("merchant")) {
                    assertThat(actualQuery.filters().merchant()).isEqualTo(expectedFilters.path("merchant").asText());
                }
            }
        }
    }

    private void assertInvariants(GoldenCase testCase, NormalizedAnalysisPlan plan,
                                  List<AnalysisResult> results) throws Exception {
        for (String invariant : testCase.invariants()) {
            switch (invariant) {
                case "NO_CROSS_LEDGER" -> assertNoCrossLedgerRows(results);
                case "EXCLUDES_TRANSFER" -> assertNoTransfers(results);
                case "CLOSED_DATE_RANGE" -> assertClosedRanges(results);
                case "NON_NEGATIVE_COUNT" -> assertNonNegativeCounts(results);
                case "NAMED_FIELDS_ONLY" -> assertNamedResultFields(results);
                case "ZERO_BUCKETS_PRESENT" -> assertZeroBuckets(plan, results);
                default -> fail("未知 Golden Set invariant: " + invariant);
            }
        }
    }

    private void assertNoCrossLedgerRows(List<AnalysisResult> results) {
        Set<String> forbiddenLabels = Set.of("其他账本账户", "其他账本标签", "其他账本商家");
        for (AnalysisResult result : results) {
            if (result instanceof BreakdownResult breakdown) {
                assertThat(breakdown.rows()).allSatisfy(row ->
                    assertThat(forbiddenLabels).doesNotContain(row.label()));
            } else if (result instanceof TransactionsResult transactions) {
                assertThat(transactions.transactions()).allSatisfy(row ->
                    assertThat(forbiddenLabels).doesNotContain(row.account(), row.merchant()));
            }
        }
    }

    private void assertNoTransfers(List<AnalysisResult> results) {
        for (AnalysisResult result : results) {
            assertThat(result.transactionType()).isNotEqualTo(TransactionType.TRANSFER);
            if (result instanceof TransactionsResult transactions) {
                assertThat(transactions.transactions()).extracting(AnalysisTransactionFact::type)
                    .doesNotContain(TransactionType.TRANSFER);
            }
        }
    }

    private void assertClosedRanges(List<AnalysisResult> results) {
        for (AnalysisResult result : results) {
            assertThat(result.period()).isNotNull();
            assertThat(result.period().current().start())
                .isBeforeOrEqualTo(result.period().current().end());
            if (result.period().previous() != null) {
                assertThat(result.period().previous().start())
                    .isBeforeOrEqualTo(result.period().previous().end());
            }
        }
    }

    private void assertNonNegativeCounts(List<AnalysisResult> results) {
        for (AnalysisResult result : results) {
            if (result instanceof AggregateResult aggregate) {
                assertThat(aggregate.values().count()).isGreaterThanOrEqualTo(0);
            } else if (result instanceof BreakdownResult breakdown) {
                assertThat(breakdown.rows()).allSatisfy(row -> assertThat(row.count()).isGreaterThanOrEqualTo(0));
            } else if (result instanceof TrendResult trend) {
                assertThat(trend.points()).allSatisfy(point -> assertThat(point.count()).isGreaterThanOrEqualTo(0));
            } else if (result instanceof AverageByPeriodResult average) {
                assertThat(average.points()).allSatisfy(point -> assertThat(point.count()).isGreaterThanOrEqualTo(0));
            } else if (result instanceof TransactionsResult transactions) {
                assertThat(transactions.values().totalCount()).isGreaterThanOrEqualTo(0);
                assertThat(transactions.values().displayedCount()).isGreaterThanOrEqualTo(0);
            }
        }
    }

    private void assertNamedResultFields(List<AnalysisResult> results) throws Exception {
        for (AnalysisResult result : results) {
            JsonNode json = objectMapper.valueToTree(result);
            assertThat(json.isObject()).isTrue();
            assertThat(json.has("kind")).isTrue();
            assertThat(json.has("title")).isTrue();
        }
    }

    private void assertZeroBuckets(NormalizedAnalysisPlan plan, List<AnalysisResult> results) {
        AnalysisTimeGrain grain = plan.queries().stream()
            .filter(query -> query.kind() == AnalysisQueryKind.TREND)
            .map(NormalizedAnalysisQuery::timeGrain)
            .findFirst()
            .orElseThrow(() -> new AssertionError("ZERO_BUCKETS_PRESENT requires a TREND query"));
        int expected = periodResolver.bucketCount(results.stream()
            .filter(result -> result instanceof TrendResult || result instanceof AverageByPeriodResult)
            .findFirst().orElseThrow().period().current(), grain);
        results.stream()
            .filter(result -> result instanceof TrendResult || result instanceof AverageByPeriodResult)
            .forEach(result -> {
                List<String> periods = result instanceof TrendResult trend
                    ? trend.points().stream().map(AnalysisTrendFact.Point::period).toList()
                    : ((AverageByPeriodResult) result).points().stream()
                        .map(AnalysisTrendFact.Point::period).toList();
                assertThat(periods).hasSize(expected);
                assertThat(new HashSet<>(periods)).hasSize(periods.size());
            });
    }

    record GoldenCase(
        String id,
        String question,
        List<String> turns,
        AnalysisResponseStatus expectedStatus,
        JsonNode expectedPlan,
        List<String> invariants) {}

    interface GoldenFixturePlanner {
        NormalizedAnalysisPlan plan(String question, List<String> turns);
    }

    static final class JsonGoldenFixturePlanner implements GoldenFixturePlanner {
        private final Map<String, NormalizedAnalysisPlan> plansByQuestion;

        JsonGoldenFixturePlanner(List<GoldenCase> cases) {
            Map<String, NormalizedAnalysisPlan> mapped = new LinkedHashMap<>();
            for (GoldenCase testCase : cases) {
                mapped.put(testCase.question(), buildPlan(testCase.expectedPlan()));
            }
            this.plansByQuestion = Map.copyOf(mapped);
        }

        @Override
        public NormalizedAnalysisPlan plan(String question, List<String> turns) {
            NormalizedAnalysisPlan plan = plansByQuestion.get(question);
            if (plan == null) {
                throw new AssertionError("未知 Golden Set question: " + question);
            }
            return plan;
        }

        private static NormalizedAnalysisPlan buildPlan(JsonNode expected) {
            AnalysisOperationKind operation = AnalysisOperationKind.valueOf(expected.path("operation").asText());
            AnalysisComparisonMode comparisonMode = expected.hasNonNull("comparisonMode")
                ? AnalysisComparisonMode.valueOf(expected.path("comparisonMode").asText())
                : null;
            List<NormalizedAnalysisQuery> queries = new ArrayList<>();
            JsonNode expectedQueries = expected.path("queries");
            for (int i = 0; i < expectedQueries.size(); i++) {
                queries.add(buildQuery(expectedQueries.get(i), i));
            }
            return new NormalizedAnalysisPlan(operation, comparisonMode, queries, expected.path("title").asText(null));
        }

        private static NormalizedAnalysisQuery buildQuery(JsonNode expectedQuery, int index) {
            LocalDate start = LocalDate.parse(expectedQuery.path("periodStart").asText());
            LocalDate end = LocalDate.parse(expectedQuery.path("periodEnd").asText());
            AnalysisPeriodSpec periodSemantic = periodSpec(expectedQuery, start, end);
            return new NormalizedAnalysisQuery(
                expectedQuery.path("id").asText("q" + (index + 1)),
                AnalysisQueryKind.valueOf(expectedQuery.path("kind").asText()),
                AnalysisMetric.valueOf(expectedQuery.path("metric").asText("SUM")),
                expectedQuery.hasNonNull("transactionType")
                    ? TransactionType.valueOf(expectedQuery.path("transactionType").asText())
                    : null,
                periodSemantic,
                new AnalysisDateRange(start, end),
                buildFilters(expectedQuery.path("filters")),
                expectedQuery.hasNonNull("dimension")
                    ? AnalysisDimension.valueOf(expectedQuery.path("dimension").asText())
                    : null,
                expectedQuery.hasNonNull("timeGrain")
                    ? AnalysisTimeGrain.valueOf(expectedQuery.path("timeGrain").asText())
                    : null,
                expectedQuery.hasNonNull("sortField")
                    ? AnalysisSortField.valueOf(expectedQuery.path("sortField").asText())
                    : AnalysisSortField.AMOUNT,
                expectedQuery.hasNonNull("sortDirection")
                    ? AnalysisSortDirection.valueOf(expectedQuery.path("sortDirection").asText())
                    : AnalysisSortDirection.DESC,
                expectedQuery.path("limit").asInt(10),
                expectedQuery.path("title").asText(null));
        }

        private static AnalysisPeriodSpec periodSpec(JsonNode expectedQuery, LocalDate start, LocalDate end) {
            if (!expectedQuery.hasNonNull("periodPreset")) {
                return AnalysisPeriodSpec.explicit(start, end);
            }
            AnalysisPeriodPreset preset = AnalysisPeriodPreset.valueOf(expectedQuery.path("periodPreset").asText());
            return switch (preset) {
                case EXPLICIT_RANGE -> AnalysisPeriodSpec.explicit(start, end);
                case LAST_N_MONTHS -> AnalysisPeriodSpec.lastNMonths(expectedQuery.path("periodCount").asInt(1));
                default -> AnalysisPeriodSpec.of(preset);
            };
        }

        private static AnalysisFilters buildFilters(JsonNode expectedFilters) {
            if (expectedFilters == null || expectedFilters.isMissingNode() || expectedFilters.isNull()
                    || !expectedFilters.isObject()) {
                return AnalysisFilters.empty();
            }
            List<Long> tagIds = new ArrayList<>();
            if (expectedFilters.has("tagIds") && expectedFilters.path("tagIds").isArray()) {
                expectedFilters.path("tagIds").forEach(node -> tagIds.add(node.asLong()));
            }
            return new AnalysisFilters(
                longOrNull(expectedFilters, "categoryId"),
                longOrNull(expectedFilters, "accountId"),
                tagIds,
                textOrNull(expectedFilters, "merchant"),
                textOrNull(expectedFilters, "keyword"),
                decimalOrNull(expectedFilters, "minAmount"),
                decimalOrNull(expectedFilters, "maxAmount"));
        }

        private static Long longOrNull(JsonNode node, String field) {
            return node.hasNonNull(field) ? node.path(field).asLong() : null;
        }

        private static String textOrNull(JsonNode node, String field) {
            return node.hasNonNull(field) ? node.path(field).asText() : null;
        }

        private static BigDecimal decimalOrNull(JsonNode node, String field) {
            return node.hasNonNull(field) ? new BigDecimal(node.path(field).asText()) : null;
        }
    }
}
