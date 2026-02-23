package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class ErrorAggregator {

    @Value("${analysis.time-window-minutes:5}")
    private int timeWindowMinutes;

    @Value("${analysis.spike-threshold:3}")
    private int spikeThreshold;

    public List<ErrorGroup> aggregate(List<LogEntry> entries) {

        Map<String, List<LogEntry>> grouped = new LinkedHashMap<>();

        for(LogEntry entry : entries) {
            String key = entry.getErrorType() + "|" + getTimeBucket(entry.getTimestamp());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }

        List<ErrorGroup> errorGroups = new ArrayList<>();

        for(Map.Entry<String, List<LogEntry>> mapEntry : grouped.entrySet()) {

            List<LogEntry> groupEntries = mapEntry.getValue();
            LogEntry first = groupEntries.get(0);

            int count = groupEntries.size();
            boolean spikeDetected = count >= spikeThreshold;
            String timeWindow = getTimeWindowLabel(first.getTimestamp());

            Map<String, List<String>> aggregatedMetadata = new LinkedHashMap<>();
            for(LogEntry e : groupEntries) {
                e.getMetadata().forEach((key, value) -> {
                    List<String> values = aggregatedMetadata.computeIfAbsent(key, k -> new ArrayList<>());
                    if (!values.contains(value)) {
                        values.add(value);
                    }
                });
            }

            ErrorGroup group = ErrorGroup.builder()
                    .errorType(first.getErrorType())
                    .timeWindow(timeWindow)
                    .count(count)
                    .spikeDetected(spikeDetected)
                    .aggregatedMetadata(aggregatedMetadata)
                    .build();

            errorGroups.add(group);
            log.info("Group: {} | window: {} | count: {} | spike: {}",
                    first.getErrorType(), timeWindow, count, spikeDetected);
        }

        return errorGroups;
    }

    private String getTimeBucket(LocalDateTime time) {
        int bucketMinute = (time.getMinute() / timeWindowMinutes) * timeWindowMinutes;
        return time.toLocalDate() + " " + time.getHour() + ":" + String.format("%02d", bucketMinute);
    }

    private String getTimeWindowLabel(LocalDateTime time) {
        int startMinute = (time.getMinute() / timeWindowMinutes) * timeWindowMinutes;
        int endMinute = startMinute + timeWindowMinutes;
        return String.format("%02d:%02d - %02d:%02d", time.getHour(), startMinute, time.getHour(), endMinute);
    }
}
