package com.app.LogAnalyser.service;

import com.app.LogAnalyser.model.AnalysisResponse;
import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.LogEntry;
import com.app.LogAnalyser.processor.AiSummaryGenerator;
import com.app.LogAnalyser.processor.ErrorAggregator;
import com.app.LogAnalyser.processor.LogParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LogAnalysisService {

    private final LogParser logParser;
    private final ErrorAggregator errorAggregator;
    private final AiSummaryGenerator aiSummaryGenerator;

    public LogAnalysisService(LogParser logParser, ErrorAggregator errorAggregator, AiSummaryGenerator aiSummaryGenerator) {
        this.logParser = logParser;
        this.errorAggregator = errorAggregator;
        this.aiSummaryGenerator = aiSummaryGenerator;
    }

    public AnalysisResponse analyze(InputStream inputStream) throws IOException {

        log.info("Starting log analysis...");

        List<LogEntry> entries = logParser.parse(inputStream);

        Map<String, Map<String, ErrorGroup>> bucketedGroups = errorAggregator.aggregate(entries);

        aiSummaryGenerator.generateAndAssign(bucketedGroups);

        log.info("Analysis complete. {} time windows found.", bucketedGroups.size());

        return AnalysisResponse.builder()
                .groups(bucketedGroups)
                .totalLinesProcessed(entries.size())
                .analyzedAt(LocalDateTime.now())
                .build();
    }
}
