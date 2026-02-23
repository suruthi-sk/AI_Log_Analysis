package com.app.LogAnalyser.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AnalysisResponse {

    private List<ErrorGroup> groups;
    private int totalLinesProcessed;
    private int totalLinesFailed;
    private LocalDateTime analyzedAt;
}
