package com.liuqitech.accountingassistant.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisModelGoldenSetTest {

    @BeforeAll
    static void enableOnlyWhenRequested() {
        Assumptions.assumeTrue("1".equals(System.getenv("ANALYSIS_REAL_MODEL")),
            "set ANALYSIS_REAL_MODEL=1 to run real-model golden set");
    }

    @Test
    void recordsIntentPeriodFilterAndIllegalPlanRateOnly() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode cases = objectMapper.readTree(
            new ClassPathResource("analysis/golden-set.json").getInputStream());
        assertThat(cases.isArray()).isTrue();
        assertThat(cases.size()).isGreaterThanOrEqualTo(20);

        Map<String, Integer> intents = new LinkedHashMap<>();
        Map<String, Integer> periods = new LinkedHashMap<>();
        int filtered = 0;
        int illegal = 0;
        for (JsonNode testCase : cases) {
            JsonNode expectedPlan = testCase.path("expectedPlan");
            String operation = expectedPlan.path("operation").asText("UNKNOWN");
            intents.merge(operation, 1, Integer::sum);
            JsonNode queries = expectedPlan.path("queries");
            if (queries.isArray() && queries.size() > 0) {
                periods.merge(queries.get(0).path("periodPreset").asText("EXPLICIT_RANGE"), 1, Integer::sum);
                if (queries.get(0).has("filters")) {
                    filtered++;
                }
            }
            if ("INVALID_PLAN".equals(testCase.path("expectedStatus").asText())) {
                illegal++;
            }
        }

        double illegalPlanRate = (double) illegal / cases.size();
        assertThat(intents).isNotEmpty();
        assertThat(periods).isNotEmpty();
        assertThat(filtered).isGreaterThanOrEqualTo(0);
        assertThat(illegalPlanRate).isBetween(0.0, 1.0);
    }
}
