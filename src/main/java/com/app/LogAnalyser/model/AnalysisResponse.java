package com.app.LogAnalyser.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class AnalysisResponse {
    private Map<String, Map<String, ErrorGroup>> groups;
    private int totalLinesProcessed;
    private LocalDateTime analyzedAt;
    private Integer matchCount;
    private Integer framesAnalyzed;
}