package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class ErrorAggregator {

    //@Value("${analysis.time-window-minutes:5}")
    private int timeWindowMinutes;

   // @Value("${analysis.spike-threshold:3}")
    private int spikeThreshold;

    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Map<String, ErrorGroup>> aggregate(List<LogEntry> entries, int timeWindows, int spikeLimit) {
        timeWindowMinutes = timeWindows;
        spikeThreshold = spikeLimit;

        Map<String, Map<String, ErrorGroup>> result = new LinkedHashMap<>();

        for(LogEntry entry : entries) {
            String timeWindow = getTimeWindowLabel(entry.getTimestamp());
            String errorType  = entry.getErrorType();

            result.computeIfAbsent(timeWindow, k -> new LinkedHashMap<>());

            Map<String, ErrorGroup> windowBucket = result.get(timeWindow);

            if(windowBucket.containsKey(errorType)) {
                ErrorGroup existing = windowBucket.get(errorType);

                existing.getTimestamps().add(entry.getTimestamp().format(OUTPUT_FORMATTER));

                existing.setCount(existing.getCount() + 1);

                existing.setSpikeDetected(existing.getCount() >= spikeThreshold);

                entry.getMetadata().forEach((key, value) -> {
                    List<String> values = existing.getAggregatedMetadata().computeIfAbsent(key, k -> new ArrayList<>());
                    if(!values.contains(value)) {
                        values.add(value);
                    }
                });

            } else {

                List<String> timestamps = new ArrayList<>();
                timestamps.add(entry.getTimestamp().format(OUTPUT_FORMATTER));

                Map<String, List<String>> aggregatedMetadata = new LinkedHashMap<>();
                entry.getMetadata().forEach((key, value) ->
                        aggregatedMetadata.computeIfAbsent(key, k -> new ArrayList<>()).add(value)
                );

                ErrorGroup group = ErrorGroup.builder()
                        .errorType(errorType)
                        .timeWindow(timeWindow)
                        .count(1)
                        .spikeDetected(false)
                        .aggregatedMetadata(aggregatedMetadata)
                        .timestamps(timestamps)
                        .build();

                windowBucket.put(errorType, group);

                log.info("New group: {} | window: {}", errorType, timeWindow);
            }
        }

        return result;
    }

    private String getTimeWindowLabel(LocalDateTime time) {

        int startMinute = (time.getMinute() / timeWindowMinutes) * timeWindowMinutes;

        LocalDateTime startTime = time
                .withMinute(startMinute)
                .withSecond(0)
                .withNano(0);

        LocalDateTime endTime = startTime.plusMinutes(timeWindowMinutes);

        return String.format("%02d:%02d - %02d:%02d",
                startTime.getHour(),
                startTime.getMinute(),
                endTime.getHour(),
                endTime.getMinute());
    }
}