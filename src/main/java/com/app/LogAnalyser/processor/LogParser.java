package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LogParser {

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2})\\s+(\\w+)\\s+(\\w+)(.*)$"
    );

    private static final Pattern METADATA_PATTERN = Pattern.compile("(\\w+)=([\\w./-]+)");

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<LogEntry> parse(String logContent) {

        List<LogEntry> entries = new ArrayList<>();
        String[] lines = logContent.split("\\r?\\n");

        for(String line : lines) {

            if(line.isBlank()) continue;

            Matcher matcher = LOG_PATTERN.matcher(line.trim());

            if(!matcher.matches()) {
                log.warn("Skipping malformed line: {}", line);
                continue;
            }

            LocalDateTime timestamp = LocalDateTime.parse(matcher.group(1), FORMATTER);
            LogEntry.LogLevel level = LogEntry.LogLevel.fromString(matcher.group(2));
            String errorType = matcher.group(3);
            String rest = matcher.group(4);

            Map<String, String> metadata = new LinkedHashMap<>();
            Matcher metaMatcher = METADATA_PATTERN.matcher(rest);
            while(metaMatcher.find()) {
                metadata.put(metaMatcher.group(1), metaMatcher.group(2));
            }

            LogEntry entry = LogEntry.builder()
                    .timestamp(timestamp)
                    .level(level)
                    .errorType(errorType)
                    .rawLine(line)
                    .metadata(metadata)
                    .build();

            entries.add(entry);
        }

        log.info("Parsed {} valid entries", entries.size());
        return entries;
    }
}
