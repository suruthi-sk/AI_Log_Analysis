package com.app.LogAnalyser.validator;

import com.app.LogAnalyser.model.APIResponse.ErrorCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class RequestValidator {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final List<String> ALLOWED_LEVELS = List.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    private static final Set<String> ALLOWED_GET_ERRORS_PARAMS = Set.of("file", "date", "fromTime", "toTime", "levels");

    private static final Set<String> ALLOWED_ANALYZE_PARAMS = Set.of("file", "date", "fromTime", "toTime", "timeWindow", "spikeLimit", "levels", "errorTypes", "messageFilters", "includeGit", "includeAi");

    private static final Set<String> ALLOWED_TRACE_ANALYZE_PARAMS = Set.of("file", "errorName", "stackTrace", "date", "fromTime", "toTime", "timeWindow", "spikeLimit", "levels", "includeGit", "includeAi");

    @Value("${analysis.validation.time-window.min:0}")
    private int timeWindowMin;

    @Value("${analysis.validation.time-window.max:60}")
    private int timeWindowMax;

    @Value("${analysis.validation.spike-limit.min:1}")
    private int spikeLimitMin;

    @Value("${analysis.validation.spike-limit.max:100}")
    private int spikeLimitMax;

    @Value("${analysis.validation.file.max-size-mb:50}")
    private long maxFileSizeMb;

    @Value("${analysis.validation.stack-trace.max-length:5000}")
    private int maxStackTraceLength;

    public ValidationResult validateParams(Map<String, String[]> receivedParams, String endpoint) {
        List<String> errors = new ArrayList<>();

        Set<String> allowedParams = resolveAllowedParams(endpoint);

        if (allowedParams == null) {
            return new ValidationResult(errors);
        }

        for (String param : receivedParams.keySet()) {
            if (!allowedParams.contains(param)) {
                log.warn("Unknown param '{}' received for endpoint '{}'", param, endpoint);
                errors.add("Unknown query parameter '" + param + "' is not allowed for this endpoint.");
            }
        }

        return new ValidationResult(errors);
    }

    public ValidationResult validateAnalyze(MultipartFile file, String date, String fromTime, String toTime, int timeWindowMinutes, int spikeLimit, String levels, String errorTypes, String messageFilters, String includeAi, String includeGit) {
        List<String> errors = new ArrayList<>();

        validateFile(file, errors);
        validateDate(date, errors);

        boolean fromOk = validateTime("fromTime", fromTime, errors);
        boolean toOk   = validateTime("toTime",   toTime,   errors);
        if(fromOk && toOk)
            validateTimeRange(fromTime, toTime, errors);

        validateTimeWindow(timeWindowMinutes, errors);
        validateSpikeLimit(spikeLimit, errors);
        validateLevels(levels, errors);
        validateErrorTypes(errorTypes, errors);
        validateMessages(messageFilters, errors);
        validateBooleanParam("includeGit", includeGit, errors);
        validateBooleanParam("includeAi", includeAi, errors);

        if(!errors.isEmpty()) log.warn("Analyze validation failed: {}", errors);
        return new ValidationResult(errors);
    }

    public ValidationResult validateGetErrors(MultipartFile file, String date, String fromTime, String toTime, String levels) {
        List<String> errors = new ArrayList<>();

        validateFile(file, errors);
        validateDate(date, errors);

        boolean fromOk = validateTime("fromTime", fromTime, errors);
        boolean toOk   = validateTime("toTime",   toTime,   errors);
        if (fromOk && toOk)
            validateTimeRange(fromTime, toTime, errors);

        validateLevels(levels, errors);

        if(!errors.isEmpty())
            log.warn("GetErrors validation failed: {}", errors);
        return new ValidationResult(errors);
    }

    public ValidationResult validateTraceAnalyze(MultipartFile file, String errorName, String stackTrace, String date, String fromTime, String toTime, int timeWindowMinutes, int spikeLimit, String levels, String includeAi) {
        List<String> errors = new ArrayList<>();

        validateFile(file, errors);
        validateErrorName(errorName, errors);
        validateStackTrace(stackTrace, errors);
        validateDate(date, errors);

        boolean fromOk = validateTime("fromTime", fromTime, errors);
        boolean toOk = validateTime("toTime",   toTime,   errors);
        if(fromOk && toOk)
            validateTimeRange(fromTime, toTime, errors);

        validateTimeWindow(timeWindowMinutes, errors);
        validateSpikeLimit(spikeLimit, errors);
        validateLevels(levels, errors);
        validateBooleanParam("includeAi", includeAi, errors);

        if(!errors.isEmpty())
            log.warn("TraceAnalyze validation failed: {}", errors);
        return new ValidationResult(errors);
    }

    private void validateErrorName(String errorName, List<String> errors) {
        if(errorName == null || errorName.isBlank()) {
            errors.add("errorName must not be blank.");
            return;
        }
        if(errorName.trim().length() > 300) {
            errors.add("errorName must be under 300 characters. Received length: " + errorName.trim().length() + ".");
        }
    }

    private void validateStackTrace(String stackTrace, List<String> errors) {
        if(stackTrace == null || stackTrace.isBlank()) {
            errors.add("stackTrace must not be blank.");
            return;
        }
        if(stackTrace.trim().length() > maxStackTraceLength) {
            errors.add("stackTrace exceeds the maximum allowed length of " + maxStackTraceLength + " characters. Received length: " + stackTrace.trim().length() + ".");
        }
    }

    private void validateFile(MultipartFile file, List<String> errors) {
        if(file == null || file.isEmpty()) {
            errors.add("File must not be empty.");
            return;
        }

        long maxBytes = maxFileSizeMb * 1024 * 1024;
        if(file.getSize() > maxBytes) {
            errors.add(String.format("File size %.2f MB exceeds the maximum allowed size of %d MB.", file.getSize() / (1024.0 * 1024.0), maxFileSizeMb));
        }

        String name = file.getOriginalFilename();
        if(name != null) {
            String lower = name.toLowerCase();
            if(!lower.endsWith(".log") && !lower.endsWith(".txt")) {
                errors.add("Only .log and .txt files are supported. Received: " + name);
            }
        }
    }

    private void validateDate(String date, List<String> errors) {
        if(date == null || date.isBlank())
            return;
        try {
            LocalDate.parse(date.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            errors.add("Invalid date format '" + date + "'. Expected: yyyy-MM-dd (e.g. 2026-01-15).");
        }
    }

    private boolean validateTime(String fieldName, String time, List<String> errors) {
        if (time == null || time.isBlank())
            return true;
        try {
            LocalTime.parse(time.trim(), TIME_FORMAT);
            return true;
        } catch (DateTimeParseException e) {
            errors.add("Invalid " + fieldName + " format '" + time + "'. Expected: HH:mm (e.g. 09:30).");
            return false;
        }
    }

    private void validateTimeRange(String fromTime, String toTime, List<String> errors) {
        if(fromTime == null || fromTime.isBlank())
            return;
        if(toTime == null || toTime.isBlank())
            return;

        LocalTime from = LocalTime.parse(fromTime.trim(), TIME_FORMAT);
        LocalTime to = LocalTime.parse(toTime.trim(),   TIME_FORMAT);

        if(!from.isBefore(to)) {
            errors.add("fromTime '" + fromTime + "' must be strictly before toTime '" + toTime + "'.");
        }
    }

    private void validateTimeWindow(int timeWindowMinutes, List<String> errors) {
        if(timeWindowMinutes < 0 || timeWindowMinutes > timeWindowMax) {
            errors.add(String.format("timeWindow must be 0 (no split) or between 1 and %d minutes. Received: %d.", timeWindowMax, timeWindowMinutes));
        }
    }

    private void validateSpikeLimit(int spikeLimit, List<String> errors) {
        if(spikeLimit < spikeLimitMin || spikeLimit > spikeLimitMax) {
            errors.add(String.format("spikeLimit must be between %d and %d. Received: %d.", spikeLimitMin, spikeLimitMax, spikeLimit));
        }
    }

    private void validateLevels(String levels, List<String> errors) {
        if(levels == null || levels.isBlank()) {
            errors.add("levels must not be blank. Provide a comma-separated list e.g. ERROR,WARN.");
            return;
        }

        List<String> unknown = new ArrayList<>();
        for(String level : levels.split(",")) {
            String trimmed = level.trim().toUpperCase();
            if(!ALLOWED_LEVELS.contains(trimmed))
                unknown.add(level.trim());
        }

        if(!unknown.isEmpty()) {
            errors.add("Unknown log level(s): " + unknown + ". Allowed values are: " + String.join(", ", ALLOWED_LEVELS) + ".");
        }
    }

    private void validateErrorTypes(String errorTypes, List<String> errors) {
        if(errorTypes == null || errorTypes.isBlank())
            return;
        for(String e : errorTypes.split(",")) {
            String trimmed = e.trim();
            if (trimmed.length() > 300) {
                errors.add("Each errorType value must be under 300 characters. Too long: '" + trimmed + "'.");
            }
        }
    }

    private void validateMessages(String messageFilters, List<String> errors) {
        if(messageFilters == null || messageFilters.isBlank())
            return;
        for(String e : messageFilters.split(",")) {
            String trimmed = e.trim();
            if (trimmed.length() > 300) {
                errors.add("Each Message value must be under 300 characters. Too long: '" + trimmed + "'.");
            }
        }
    }

    private void validateBooleanParam(String fieldName, String value, List<String> errors) {
        if(value == null || value.isBlank())
            return;
        if(!value.trim().equalsIgnoreCase("true") && !value.trim().equalsIgnoreCase("false")) {
            errors.add("'" + fieldName + "' must be 'true' or 'false'. Received: '" + value + "'.");
        }
    }

    private Set<String> resolveAllowedParams(String endpoint) {
        return switch (endpoint) {
            case "errors" -> ALLOWED_GET_ERRORS_PARAMS;
            case "analyze" -> ALLOWED_ANALYZE_PARAMS;
            case "traceAnalyse" -> ALLOWED_TRACE_ANALYZE_PARAMS;
            default -> null;
        };
    }

    @Getter
    public static class ValidationResult {

        private final List<String> errors;

        public ValidationResult(List<String> errors) {
            this.errors = errors;
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public ErrorCode errorCode() {
            return ErrorCode.VALIDATION_FAILED;
        }
    }
}