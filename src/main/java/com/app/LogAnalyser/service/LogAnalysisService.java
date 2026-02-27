package com.app.LogAnalyser.service;

import com.app.LogAnalyser.model.AnalysisResponse;
import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.LogEntry;
import com.app.LogAnalyser.processor.AiSummaryGenerator;
import com.app.LogAnalyser.processor.ErrorAggregator;
import com.app.LogAnalyser.processor.LogParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
public class LogAnalysisService {

    private final LogParser logParser;
    private final ErrorAggregator errorAggregator;
    private final AiSummaryGenerator aiSummaryGenerator;

    public LogAnalysisService(LogParser logParser, ErrorAggregator errorAggregator, AiSummaryGenerator aiSummaryGenerator) {
        this.logParser = logParser;
        this.errorAggregator = errorAggregator;
        this.aiSummaryGenerator = aiSummaryGenerator;
    }

    public AnalysisResponse analyze(InputStream inputStream, String date, String fromTime, String toTime, int timeWindowMinutes, int spikeLimit, String levels) throws IOException {
        log.info("Starting analysis | date: {} | from: {} | to: {} | window: {}min | levels: {}", date, fromTime, toTime, timeWindowMinutes, levels);

        LocalDateTime fromDateTime = buildDateTime(date, fromTime, false);
        LocalDateTime toDateTime   = buildDateTime(date, toTime,   true);

        log.info("Filter range: {} -> {}", fromDateTime, toDateTime);

        Set<LogEntry.LogLevel> acceptedLevels = parseAcceptedLevels(levels);

        List<LogEntry> entries = logParser.parse(inputStream, fromDateTime, toDateTime, acceptedLevels);

        Map<String, Map<String, ErrorGroup>> bucketedGroups = errorAggregator.aggregate(entries, timeWindowMinutes, spikeLimit);

        aiSummaryGenerator.generateAndAssign(bucketedGroups);

        log.info("Analysis complete. {} time windows found.", bucketedGroups.size());

        return AnalysisResponse.builder()
                .groups(bucketedGroups)
                .totalLinesProcessed(entries.size())
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private Set<LogEntry.LogLevel> parseAcceptedLevels(String levels) {
        Set<LogEntry.LogLevel> result = new LinkedHashSet<>();
        for (String level : levels.split(",")) {
            try {
                result.add(LogEntry.LogLevel.fromString(level.trim()));
            } catch (Exception e) {
                log.warn("Unknown level ignored: {}", level.trim());
            }
        }
        if (result.isEmpty()) {
            result.add(LogEntry.LogLevel.ERROR);
            result.add(LogEntry.LogLevel.WARN);
        }
        return result;
    }

    private LocalDateTime buildDateTime(String date, String time, boolean isEndOfDay) {
        if ((date == null || date.isBlank()) && (time == null || time.isBlank())) {
            return null;
        }

        LocalDate parsedDate;
        if(date == null || date.isBlank()) {
            parsedDate = LocalDate.now();
        } else {
            try {
                parsedDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date format. Use yyyy-MM-dd. Got: " + date);
            } catch (Exception e) {
                throw new RuntimeException("Some Error Occurred");
            }
        }

        LocalTime parsedTime;
        if(time == null || time.isBlank()) {
            parsedTime = isEndOfDay ? LocalTime.of(23, 59, 59)
                    : LocalTime.of(0, 0, 0);
        } else {
            try {
                parsedTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid time format. Use HH:mm. Got: " + time);
            } catch (Exception e) {
                throw new RuntimeException("Some Error Occurred");
            }
        }

        return LocalDateTime.of(parsedDate, parsedTime);
    }
}