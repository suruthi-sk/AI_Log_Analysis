package com.app.LogAnalyser.service;

import com.app.LogAnalyser.model.*;
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LogAnalysisService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final LogParser logParser;
    private final ErrorAggregator errorAggregator;
    private final AiSummaryGenerator aiSummaryGenerator;

    public LogAnalysisService(LogParser logParser, ErrorAggregator errorAggregator, AiSummaryGenerator aiSummaryGenerator) {
        this.logParser = logParser;
        this.errorAggregator = errorAggregator;
        this.aiSummaryGenerator = aiSummaryGenerator;
    }

    public ErrorTypesResponse getErrorTypes(InputStream inputStream, String date, String fromTime, String toTime, String levels) throws IOException {

        log.info("Discovering error types | date: {} | from: {} | to: {} | levels: {}", date, fromTime, toTime, levels);

        LocalDateTime fromDateTime = buildDateTime(date, fromTime, false);
        LocalDateTime toDateTime = buildDateTime(date, toTime, true);
        Set<LogEntry.LogLevel> acceptedLevels = parseAcceptedLevels(levels);

        List<LogEntry> entries = logParser.parse(inputStream, fromDateTime, toDateTime, acceptedLevels);

        Map<String, Integer> countMap = new LinkedHashMap<>();
        for(LogEntry entry : entries) {
            countMap.merge(entry.getErrorType(), 1, Integer::sum);
        }

        List<ErrorTypeInfo> errorTypes = countMap.entrySet().stream()
                .map(e -> ErrorTypeInfo.builder()
                        .errorType(e.getKey())
                        .count(e.getValue())
                        .build())
                .collect(Collectors.toList());

        log.info("Found {} unique error types across {} entries.", errorTypes.size(), entries.size());

        return ErrorTypesResponse.builder()
                .totalEntries(entries.size())
                .uniqueErrorCount(errorTypes.size())
                .errorTypes(errorTypes)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    public AnalysisResponse analyze(InputStream inputStream, String date, String fromTime, String toTime, int timeWindowMinutes, int spikeLimit, String levels, String errorTypes) throws IOException {

        log.info("Starting analysis | date: {} | from: {} | to: {} | window: {}min | spikeLimit: {} | levels: {} | errorTypes: '{}'", date, fromTime, toTime, timeWindowMinutes, spikeLimit, levels, errorTypes);

        LocalDateTime fromDateTime = buildDateTime(date, fromTime, false);
        LocalDateTime toDateTime = buildDateTime(date, toTime, true);
        Set<LogEntry.LogLevel> acceptedLevels = parseAcceptedLevels(levels);

        Set<String> errorTypeFilter = parseErrorTypes(errorTypes);

        List<LogEntry> entries = logParser.parse(inputStream, fromDateTime, toDateTime, acceptedLevels, errorTypeFilter);

        Map<String, Map<String, ErrorGroup>> bucketedGroups = errorAggregator.aggregate(entries, timeWindowMinutes, spikeLimit);

        aiSummaryGenerator.generateAndAssign(bucketedGroups);

        log.info("Analysis complete. {} time windows, {} entries processed.", bucketedGroups.size(), entries.size());

        return AnalysisResponse.builder()
                .groups(bucketedGroups)
                .totalLinesProcessed(entries.size())
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private Set<LogEntry.LogLevel> parseAcceptedLevels(String levels) {
        Set<LogEntry.LogLevel> result = new LinkedHashSet<>();
        for(String level : levels.split(",")) {
            result.add(LogEntry.LogLevel.fromString(level.trim()));
        }

        if(result.isEmpty()) {
            result.add(LogEntry.LogLevel.ERROR);
            result.add(LogEntry.LogLevel.WARN);
        }
        return result;
    }

    private Set<String> parseErrorTypes(String errorTypes) {
        if (errorTypes == null || errorTypes.isBlank())
            return null;
        Set<String> result = new LinkedHashSet<>();
        for(String e : errorTypes.split(",")) {
            String trimmed = e.trim();
            if(!trimmed.isEmpty())
                result.add(trimmed);
        }
        return result.isEmpty() ? null : result;
    }

    private LocalDateTime buildDateTime(String date, String time, boolean isEndOfDay) {
        if ((date == null || date.isBlank()) && (time == null || time.isBlank())) {
            return null;
        }

        LocalDate parsedDate = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date.trim(), DATE_FORMAT);
        LocalTime parsedTime = (time == null || time.isBlank()) ? (isEndOfDay ? LocalTime.of(23, 59, 59) : LocalTime.MIDNIGHT) : LocalTime.parse(time.trim(), TIME_FORMAT);
        return LocalDateTime.of(parsedDate, parsedTime);
    }
}