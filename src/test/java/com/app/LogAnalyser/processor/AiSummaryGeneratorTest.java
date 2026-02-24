package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.AiSummary;
import com.app.LogAnalyser.model.ErrorGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiSummaryGeneratorTest {

    private AiSummaryGenerator aiSummaryGenerator;

    @BeforeEach
    void setUp() {
        aiSummaryGenerator = new AiSummaryGenerator(new RestTemplate());
        ReflectionTestUtils.setField(aiSummaryGenerator, "apiUrl", "mock");
        ReflectionTestUtils.setField(aiSummaryGenerator, "model", "gemma3:1b");
    }

    @Test
    void testMockSummaryReturnedWhenNoApiConfigured() {
        List<AiSummary> summaries = aiSummaryGenerator
                .generateAll(List.of(buildGroup("DBConnectionTimeout")));

        assertEquals(1, summaries.size());
        assertTrue(summaries.get(0).isMockFallback());
    }

    @Test
    void testSummaryNotNullForAnyField() {
        List<AiSummary> summaries = aiSummaryGenerator
                .generateAll(List.of(buildGroup("DBConnectionTimeout")));

        AiSummary summary = summaries.get(0);
        assertNotNull(summary.getProblemSummary());
        assertNotNull(summary.getRootCause());
        assertNotNull(summary.getSuggestedFix());
    }

    @Test
    void testSummaryContainsErrorType() {
        List<AiSummary> summaries = aiSummaryGenerator
                .generateAll(List.of(buildGroup("DBConnectionTimeout")));

        assertTrue(summaries.get(0).getProblemSummary()
                .contains("DBConnectionTimeout"));
    }

    @Test
    void testCacheReturnsSameObjectForSameErrorType() {
        ErrorGroup group1 = buildGroup("DBConnectionTimeout");
        ErrorGroup group2 = buildGroup("DBConnectionTimeout");

        List<AiSummary> first  = aiSummaryGenerator.generateAll(List.of(group1));
        List<AiSummary> second = aiSummaryGenerator.generateAll(List.of(group2));

        assertSame(first.get(0), second.get(0));
    }

    @Test
    void testDifferentErrorTypesGetDifferentSummaries() {
        List<AiSummary> summaries = aiSummaryGenerator.generateAll(List.of(
                buildGroup("DBConnectionTimeout"),
                buildGroup("HighLatency")
        ));

        assertEquals(2, summaries.size());
        assertNotSame(summaries.get(0), summaries.get(1));
    }

    @Test
    void testEmptyGroupsReturnsEmptyList() {
        List<AiSummary> summaries = aiSummaryGenerator.generateAll(List.of());
        assertEquals(0, summaries.size());
    }

    private ErrorGroup buildGroup(String errorType) {
        return ErrorGroup.builder()
                .errorType(errorType)
                .timeWindow("10:00 - 10:05")
                .count(3)
                .spikeDetected(true)
                .aggregatedMetadata(Map.of())
                .build();
    }
}