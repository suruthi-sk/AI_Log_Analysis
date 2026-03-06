package com.app.LogAnalyser.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)

public class ErrorGroup {
    private String errorType;
    private String timeWindow;
    private int count;
    private boolean spikeDetected;
    private List<String> timestamps;
    private Map<String, List<String>> aggregatedMetadata;
    private AiSummary aiSummary;
    private GitAnalysisResult gitAnalysisResult;

    public String toStructuredSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Error Type: ").append(errorType).append("\n");
        sb.append("Time Window: ").append(timeWindow).append("\n");
        sb.append("Occurrences: ").append(count).append("\n");
        sb.append("Spike Detected: ").append(spikeDetected).append("\n");
        if (aggregatedMetadata != null) {
            aggregatedMetadata.forEach((key, values) ->
                    sb.append(key).append(": ").append(String.join(", ", values)).append("\n")
            );
        }
        return sb.toString();
    }
}