package com.app.LogAnalyser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSummary implements Serializable {

    private String problemSummary;
    private String rootCause;
    private String suggestedFix;
    private boolean mockFallback;

    public static AiSummary fallback(String errorType) {
        return AiSummary.builder()
                .problemSummary("Repeated occurrences of [" + errorType + "] detected.")
                .rootCause("AI service unavailable. Manual investigation required.")
                .suggestedFix("Check service health dashboards and related logs.")
                .mockFallback(true)
                .build();
    }
}