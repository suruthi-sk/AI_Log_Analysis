package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
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

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+[+\\-]\\d{2}:\\d{2})\\s+" +
                    "(\\w+)\\s+" +
                    "(\\d+)\\s+---\\s+" +
                    "\\[([^\\]]+)\\]\\s+" +
                    "\\[([^\\]]+)\\]\\s+" +
                    "([\\w.$\\[\\]/]+)\\s*:\\s+" +
                    "(.+)$"
    );

    private static final Pattern TRACE_PATTERN = Pattern.compile(
            "^([\\w.$]+):\\s*(.+)$"
    );

    private static final Pattern TIMESTAMP_START = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+[+\\-]\\d{2}:\\d{2}.*"
    );

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    public List<LogEntry> parse(InputStream inputStream, LocalDateTime fromDateTime, LocalDateTime toDateTime, Set<LogEntry.LogLevel> acceptedLevels) throws IOException {
        List<LogEntry> entries = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader( new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            StringBuilder currentBlock = new StringBuilder();

            while((line = reader.readLine()) != null) {
                if(TIMESTAMP_START.matcher(line).matches()) {
                    if(!currentBlock.isEmpty()) {
                        LogEntry entry = buildEntry(currentBlock.toString(), fromDateTime, toDateTime, acceptedLevels);
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
                LogEntry entry = buildEntry(currentBlock.toString(), fromDateTime, toDateTime, acceptedLevels);
                if (entry != null)
                    entries.add(entry);
            }
        }

        log.info("Parsed {} valid entries", entries.size());
        return entries;
    }

    private LogEntry buildEntry(String block, LocalDateTime from, LocalDateTime to, Set<LogEntry.LogLevel> acceptedLevels) {

        String[] lines = block.split("\n");

        Matcher matcher = LOG_PATTERN.matcher(lines[0].trim());
        if(!matcher.matches()) {
            log.warn("Could not parse header line: {}", lines[0]);
            return null;
        }

        String rawTimestamp = matcher.group(1);
        String rawLevel     = matcher.group(2);
        String pid          = matcher.group(3);
        String projectName  = matcher.group(4).trim();
        String thread       = matcher.group(5).trim();
        String logger       = matcher.group(6).trim();
        String message      = matcher.group(7).trim();

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(rawTimestamp, FORMATTER);
        } catch (Exception e) {
            log.warn("Could not parse timestamp: {}", rawTimestamp);
            return null;
        }

        if (!isWithinRange(timestamp, from, to)) {
            log.debug("Skipping entry outside time range: {}", timestamp);
            return null;
        }

        LogEntry.LogLevel level = LogEntry.LogLevel.fromString(rawLevel);

        if (!acceptedLevels.contains(level)) {
            log.debug("Skipping level: {}", level);
            return null;
        }

        String errorType      = null;
        String reasonForError = null;
        String stackTrace     = null;

        int blankLineIndex = -1;
        for(int i = 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                blankLineIndex = i;
                break;
            }
        }

        if(blankLineIndex != -1) {
            int errorLineIndex = blankLineIndex + 1;

            if(errorLineIndex < lines.length) {
                Matcher traceMatcher = TRACE_PATTERN.matcher(lines[errorLineIndex].trim());
                if(traceMatcher.matches()) {
                    errorType      = traceMatcher.group(1).trim();
                    reasonForError = traceMatcher.group(2).trim();
                }
            }

            int stackStartIndex = blankLineIndex + 2;
            if(stackStartIndex < lines.length) {
                String traceBlock = String.join("\n", Arrays.copyOfRange(lines, stackStartIndex, lines.length));
                if (!traceBlock.isBlank()) {
                    stackTrace = traceBlock.trim();
                }
            }
        }

        if(errorType == null) {
            if(level == LogEntry.LogLevel.ERROR || level == LogEntry.LogLevel.WARN) {
                errorType = message;
            } else {
                errorType = "Information";
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
        if(from != null && timestamp.isBefore(from)) return false;
        if(to   != null && timestamp.isAfter(to))   return false;
        return true;
    }
}