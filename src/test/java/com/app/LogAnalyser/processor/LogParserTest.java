package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogParserTest {

    private LogParser logParser;

    @BeforeEach
    void setUp() {
        logParser = new LogParser();
    }

    @Test
    void testValidLinesAreParsedCorrectly() {
        String logs = "2026-02-18 10:02:01 ERROR DBConnectionTimeout tenant=us-west-2\n" +
                "2026-02-18 10:03:10 WARN HighLatency service=alerts";

        List<LogEntry> entries = logParser.parse(logs);

        assertEquals(2, entries.size());
        assertEquals("DBConnectionTimeout", entries.get(0).getErrorType());
        assertEquals(LogEntry.LogLevel.ERROR, entries.get(0).getLevel());
        assertEquals("us-west-2", entries.get(0).getMetadata().get("tenant"));
        assertEquals("HighLatency", entries.get(1).getErrorType());
        assertEquals(LogEntry.LogLevel.WARN, entries.get(1).getLevel());
    }

    @Test
    void testInValidLinesAreSkipped() {
        String logs = "2026-02-18 10:02:01 ERROR DBConnectionTimeout tenant=us-west-2\n" +
                "this is a malformed line\n" +
                "another bad line\n" +
                "2026-02-18 10:03:10 WARN HighLatency service=alerts";

        List<LogEntry> entries = logParser.parse(logs);

        assertEquals(2, entries.size());
    }

    @Test
    void testEmptyInputReturnsEmptyList() {
        List<LogEntry> entries = logParser.parse("");
        assertEquals(0, entries.size());
    }

    @Test
    void testMetadataExtractedCorrectly() {
        String logs = "2026-02-18 10:02:01 ERROR DBConnectionTimeout tenant=us-west-2";

        List<LogEntry> entries = logParser.parse(logs);

        assertEquals(1, entries.size());
        assertEquals("us-west-2", entries.get(0).getMetadata().get("tenant"));
    }

    @Test
    void testTimestampParsedCorrectly() {
        String logs = "2026-02-18 10:02:01 ERROR DBConnectionTimeout tenant=us-west-2";

        List<LogEntry> entries = logParser.parse(logs);

        assertEquals(2026, entries.get(0).getTimestamp().getYear());
        assertEquals(2,    entries.get(0).getTimestamp().getMonthValue());
        assertEquals(18,   entries.get(0).getTimestamp().getDayOfMonth());
        assertEquals(10,   entries.get(0).getTimestamp().getHour());
        assertEquals(2,    entries.get(0).getTimestamp().getMinute());
    }

    @Test
    void testMultipleMetadataExtracted() {
        String logs = "2026-02-18 10:02:01 ERROR DBConnectionTimeout tenant=us-west-2 db=orders-db";

        List<LogEntry> entries = logParser.parse(logs);

        assertEquals("us-west-2", entries.get(0).getMetadata().get("tenant"));
        assertEquals("orders-db", entries.get(0).getMetadata().get("db"));
    }

    @Test
    void testBlankLinesAreIgnored() {
        String logs = "2026-02-18 10:02:01 ERROR DBConnectionTimeout tenant=us-west-2\n" +
                "\n" +
                "\n" +
                "2026-02-18 10:03:10 WARN HighLatency service=alerts";

        List<LogEntry> entries = logParser.parse(logs);

        assertEquals(2, entries.size());
    }
}