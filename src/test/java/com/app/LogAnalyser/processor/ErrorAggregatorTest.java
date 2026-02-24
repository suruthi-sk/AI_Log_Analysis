package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorAggregatorTest {

    private ErrorAggregator errorAggregator;

    @BeforeEach
    void setUp() {
        errorAggregator = new ErrorAggregator();
        ReflectionTestUtils.setField(errorAggregator, "timeWindowMinutes", 5);
        ReflectionTestUtils.setField(errorAggregator, "spikeThreshold", 3);
    }

    @Test
    void testEntriesGroupedByErrorType() {
        List<LogEntry> entries = List.of(
                buildEntry("DBConnectionTimeout", "2026-02-18T10:01:00", Map.of()),
                buildEntry("DBConnectionTimeout", "2026-02-18T10:02:00", Map.of()),
                buildEntry("HighLatency",         "2026-02-18T10:03:00", Map.of())
        );

        List<ErrorGroup> groups = errorAggregator.aggregate(entries);

        assertEquals(2, groups.size());
    }

    @Test
    void testSpikeDetectedWhenCountMeetsThreshold() {
        List<LogEntry> entries = List.of(
                buildEntry("DBConnectionTimeout", "2026-02-18T10:01:00", Map.of()),
                buildEntry("DBConnectionTimeout", "2026-02-18T10:02:00", Map.of()),
                buildEntry("DBConnectionTimeout", "2026-02-18T10:03:00", Map.of())
        );

        List<ErrorGroup> groups = errorAggregator.aggregate(entries);

        assertEquals(1, groups.size());
        assertTrue(groups.get(0).isSpikeDetected());
    }

    @Test
    void testNoSpikeWhenCountBelowThreshold() {
        List<LogEntry> entries = List.of(
                buildEntry("HighLatency", "2026-02-18T10:01:00", Map.of()),
                buildEntry("HighLatency", "2026-02-18T10:02:00", Map.of())
        );

        List<ErrorGroup> groups = errorAggregator.aggregate(entries);

        assertFalse(groups.get(0).isSpikeDetected());
    }

    @Test
    void testDifferentTimeWindowsCreateSeparateGroups() {
        List<LogEntry> entries = List.of(
                buildEntry("DBConnectionTimeout", "2026-02-18T10:01:00", Map.of()),
                buildEntry("DBConnectionTimeout", "2026-02-18T10:07:00", Map.of())
        );

        List<ErrorGroup> groups = errorAggregator.aggregate(entries);

        assertEquals(2, groups.size());
    }

    @Test
    void testDuplicateMetadataValuesAreRemoved() {
        List<LogEntry> entries = List.of(
                buildEntry("DBConnectionTimeout", "2026-02-18T10:01:00", Map.of("tenant", "us-west-2")),
                buildEntry("DBConnectionTimeout", "2026-02-18T10:02:00", Map.of("tenant", "us-west-2")),
                buildEntry("DBConnectionTimeout", "2026-02-18T10:03:00", Map.of("tenant", "eu-west-1"))
        );

        List<ErrorGroup> groups = errorAggregator.aggregate(entries);

        List<String> tenants = groups.get(0).getAggregatedMetadata().get("tenant");

        assertEquals(2, tenants.size());
        assertTrue(tenants.contains("us-west-2"));
        assertTrue(tenants.contains("eu-west-1"));
    }

    @Test
    void testCountIsCorrect() {
        List<LogEntry> entries = List.of(
                buildEntry("DBConnectionTimeout", "2026-02-18T10:01:00", Map.of()),
                buildEntry("DBConnectionTimeout", "2026-02-18T10:02:00", Map.of()),
                buildEntry("DBConnectionTimeout", "2026-02-18T10:03:00", Map.of()),
                buildEntry("DBConnectionTimeout", "2026-02-18T10:04:00", Map.of())
        );

        List<ErrorGroup> groups = errorAggregator.aggregate(entries);

        assertEquals(4, groups.get(0).getCount());
    }

    @Test
    void testEmptyEntriesReturnsEmptyGroups() {
        List<ErrorGroup> groups = errorAggregator.aggregate(List.of());
        assertEquals(0, groups.size());
    }

    private LogEntry buildEntry(String errorType, String timestamp, Map<String, String> metadata) {
        return LogEntry.builder()
                .errorType(errorType)
                .level(LogEntry.LogLevel.ERROR)
                .timestamp(LocalDateTime.parse(timestamp))
                .rawLine("raw line")
                .metadata(metadata)
                .build();
    }
}