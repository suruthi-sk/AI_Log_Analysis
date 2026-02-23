package com.app.LogAnalyser.model;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class LogEntry {

    private LocalDateTime timestamp;
    private LogLevel level;
    private String errorType;
    private String rawLine;
    private Map<String, String> metadata;

    public enum LogLevel {
        ERROR, WARN, INFO, DEBUG, TRACE, UNKNOWN;

        public static LogLevel fromString(String value) {
            try {
                return LogLevel.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }
}