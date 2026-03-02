package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LogParser {

    private final Pattern logPattern;
    private final Pattern tracePattern;
    private final Pattern timeStampStart;
    private final DateTimeFormatter formatter;

    public LogParser(
            @Value("${log.parser.pattern.log}") String logPatternStr,
            @Value("${log.parser.pattern.trace}") String tracePatternStr,
            @Value("${log.parser.pattern.timestamp-start}") String timestampStartStr,
            @Value("${log.parser.timestamp.format}") String timestampFormat) {

        this.logPattern = Pattern.compile(logPatternStr);
        this.tracePattern = Pattern.compile(tracePatternStr);
        this.timeStampStart = Pattern.compile(timestampStartStr);
        this.formatter = DateTimeFormatter.ofPattern(timestampFormat);

        log.info("LogParser initialised with patterns from properties.");
    }

    public List<LogEntry> parse(InputStream inputStream, LocalDateTime fromDateTime, LocalDateTime toDateTime, Set<LogEntry.LogLevel> acceptedLevels) throws IOException {
        return parse(inputStream, fromDateTime, toDateTime, acceptedLevels, null);
    }

    public List<LogEntry> parse(InputStream inputStream, LocalDateTime fromDateTime, LocalDateTime toDateTime, Set<LogEntry.LogLevel> acceptedLevels, Set<String> errorTypeFilter) throws IOException {
        List<LogEntry> entries = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder currentBlock = new StringBuilder();

            while((line = reader.readLine()) != null) {
                if(timeStampStart.matcher(line).matches()) {
                    if(!currentBlock.isEmpty()) {
                        LogEntry entry = buildEntry(currentBlock.toString(), fromDateTime, toDateTime, acceptedLevels, errorTypeFilter);
                        if(entry != null)
                            entries.add(entry);
                        currentBlock.setLength(0);
                    }
                    currentBlock.append(line);
                } else {
                    if(!currentBlock.isEmpty()) {
                        currentBlock.append("\n").append(line);
                    }
                }
            }

            if(!currentBlock.isEmpty()) {
                LogEntry entry = buildEntry(currentBlock.toString(), fromDateTime, toDateTime, acceptedLevels, errorTypeFilter);
                if(entry != null)
                    entries.add(entry);
            }
        }

        log.info("Parsed {} valid entries (errorTypeFilter='{}')", entries.size(), errorTypeFilter);
        return entries;
    }

    private LogEntry buildEntry(String block, LocalDateTime from, LocalDateTime to, Set<LogEntry.LogLevel> acceptedLevels, Set<String> errorTypeFilter) {
        String[] lines = block.split("\n");

        Matcher matcher = logPattern.matcher(lines[0].trim());
        if(!matcher.matches()) {
            log.warn("Could not parse header line: {}", lines[0]);
            return null;
        }

        String rawTimestamp = matcher.group(1);
        String rawLevel = matcher.group(2);
        String pid = matcher.group(3);
        String projectName  = matcher.group(4).trim();
        String thread = matcher.group(5).trim();
        String logger = matcher.group(6).trim();
        String message = matcher.group(7).trim();

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(rawTimestamp, formatter);
        } catch (Exception e) {
            log.warn("Could not parse timestamp: {}", rawTimestamp);
            return null;
        }

        if(!isWithinRange(timestamp, from, to)) {
            log.debug("Skipping entry outside time range: {}", timestamp);
            return null;
        }

        LogEntry.LogLevel level = LogEntry.LogLevel.fromString(rawLevel);
        if(!acceptedLevels.contains(level)) {
            log.debug("Skipping level: {}", level);
            return null;
        }

        String errorType = null;
        String reasonForError = null;
        String stackTrace = null;

        int blankLineIndex = -1;
        for(int i = 1; i < lines.length; i++) {
            if(lines[i].trim().isEmpty()) {
                blankLineIndex = i;
                break;
            }
        }

        if(blankLineIndex != -1) {
            int errorLineIndex = blankLineIndex + 1;
            if(errorLineIndex < lines.length) {
                Matcher traceMatcher = tracePattern.matcher(lines[errorLineIndex].trim());
                if(traceMatcher.matches()) {
                    errorType = traceMatcher.group(1).trim();
                    reasonForError = traceMatcher.group(2).trim();
                }
            }

            int stackStartIndex = blankLineIndex + 1;
            int stackEndIndex = Math.min(stackStartIndex + 10, lines.length);
            if(stackStartIndex < lines.length) {
                String traceBlock = String.join("\n", Arrays.copyOfRange(lines, stackStartIndex, stackEndIndex));
                if(!traceBlock.isBlank()) stackTrace = traceBlock.trim();
            }
        }

        if(errorType == null) {
            errorType = (level == LogEntry.LogLevel.ERROR || level == LogEntry.LogLevel.WARN) ? message : "Information";
        }

        if(errorTypeFilter != null && !errorTypeFilter.isEmpty()) {
            if(!errorTypeFilter.contains(errorType)) {
                log.debug("Skipping entry — errorType '{}' not in filter set {}", errorType, errorTypeFilter);
                return null;
            }
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("pid", pid);
        metadata.put("projectName", projectName);
        metadata.put("thread", thread);
        metadata.put("logger", logger);
        if(stackTrace != null)
            metadata.put("stackTrace", stackTrace);

        return LogEntry.builder()
                .timestamp(timestamp)
                .level(level)
                .message(message)
                .errorType(errorType)
                .reasonForError(reasonForError)
                .rawLine(block)
                .metadata(metadata)
                .build();
    }

    private boolean isWithinRange(LocalDateTime timestamp, LocalDateTime from, LocalDateTime to) {
        if(from != null && timestamp.isBefore(from))
            return false;
        if(to != null && timestamp.isAfter(to))
            return false;
        return true;
    }
}