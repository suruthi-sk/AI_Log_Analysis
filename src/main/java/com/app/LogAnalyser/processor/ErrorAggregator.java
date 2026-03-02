package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class ErrorAggregator {

    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public Map<String, Map<String, ErrorGroup>> aggregate(List<LogEntry> entries, int timeWindowMinutes, int spikeThreshold) {
        if(timeWindowMinutes == 0) {
            return aggregateNoSplit(entries, spikeThreshold);
        }
        return aggregateByWindow(entries, timeWindowMinutes, spikeThreshold);
    }

    private Map<String, Map<String, ErrorGroup>> aggregateNoSplit(List<LogEntry> entries, int spikeThreshold) {
        if(entries.isEmpty()) {
            log.info("No entries to aggregate.");
            return new LinkedHashMap<>();
        }

        LocalDateTime earliest = entries.stream()
                .map(LogEntry::getTimestamp)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        LocalDateTime latest = entries.stream()
                .map(LogEntry::getTimestamp)
                .max(Comparator.naturalOrder())
                .orElseThrow();

        String bucketLabel = earliest.format(LABEL_FORMATTER) + " - " + latest.format(LABEL_FORMATTER);

        log.info("No-split mode. Single bucket label: '{}'", bucketLabel);

        Map<String, ErrorGroup> bucket = new LinkedHashMap<>();
        for(LogEntry entry : entries) {
            addEntryToBucket(bucket, entry, bucketLabel, spikeThreshold);
        }

        Map<String, Map<String, ErrorGroup>> result = new LinkedHashMap<>();
        result.put(bucketLabel, bucket);
        return result;
    }

    private Map<String, Map<String, ErrorGroup>> aggregateByWindow(List<LogEntry> entries, int timeWindowMinutes, int spikeThreshold) {
        Map<String, Map<String, ErrorGroup>> result = new LinkedHashMap<>();

        for(LogEntry entry : entries) {
            String windowLabel = getTimeWindowLabel(entry.getTimestamp(), timeWindowMinutes);
            result.computeIfAbsent(windowLabel, k -> new LinkedHashMap<>());
            addEntryToBucket(result.get(windowLabel), entry, windowLabel, spikeThreshold);
        }

        return result;
    }

    private void addEntryToBucket(Map<String, ErrorGroup> bucket, LogEntry entry, String windowLabel, int spikeThreshold) {
        String errorType = entry.getErrorType();

        if(bucket.containsKey(errorType)) {
            ErrorGroup existing = bucket.get(errorType);

            existing.getTimestamps().add(entry.getTimestamp().format(OUTPUT_FORMATTER));
            existing.setCount(existing.getCount() + 1);
            existing.setSpikeDetected(existing.getCount() >= spikeThreshold);

            entry.getMetadata().forEach((key, value) -> {
                List<String> values = existing.getAggregatedMetadata().computeIfAbsent(key, k -> new ArrayList<>());
                if(!values.contains(value))
                    values.add(value);
            });

        } else {
            List<String> timestamps = new ArrayList<>();
            timestamps.add(entry.getTimestamp().format(OUTPUT_FORMATTER));

            Map<String, List<String>> aggregatedMetadata = new LinkedHashMap<>();
            entry.getMetadata().forEach((key, value) ->
                    aggregatedMetadata.computeIfAbsent(key, k -> new ArrayList<>()).add(value));

            ErrorGroup group = ErrorGroup.builder()
                    .errorType(errorType)
                    .timeWindow(windowLabel)
                    .count(1)
                    .spikeDetected(1 >= spikeThreshold)
                    .aggregatedMetadata(aggregatedMetadata)
                    .timestamps(timestamps)
                    .build();

            bucket.put(errorType, group);
            log.info("New group: '{}' | window: '{}'", errorType, windowLabel);
        }
    }

    private String getTimeWindowLabel(LocalDateTime time, int timeWindowMinutes) {
        int startMinute = (time.getMinute() / timeWindowMinutes) * timeWindowMinutes;

        LocalDateTime startTime = time.withMinute(startMinute).withSecond(0).withNano(0);
        LocalDateTime endTime = startTime.plusMinutes(timeWindowMinutes);

        return String.format("%02d:%02d - %02d:%02d", startTime.getHour(), startTime.getMinute(), endTime.getHour(),   endTime.getMinute());
    }
}