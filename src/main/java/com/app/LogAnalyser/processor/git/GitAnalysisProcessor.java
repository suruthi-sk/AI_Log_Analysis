package com.app.LogAnalyser.processor.git;

import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.GitAnalysisResult;
import com.app.LogAnalyser.processor.StackTraceParser;
import com.app.LogAnalyser.processor.StackTraceParser.ParsedFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GitAnalysisProcessor {

    private final GitAnalyser gitAnalyser;
    private final StackTraceParser stackTraceParser;

    public GitAnalysisProcessor(GitAnalyser gitAnalyser, StackTraceParser stackTraceParser) {
        this.gitAnalyser = gitAnalyser;
        this.stackTraceParser = stackTraceParser;
    }

    public void analyseAndAssign(Map<String, Map<String, ErrorGroup>> bucketedGroups) {
        bucketedGroups.forEach((timeWindow, errorGroups) -> {
            log.info("Running Git analysis for time window: {}", timeWindow);
            errorGroups.forEach((errorType, group) ->
                    group.setGitAnalysisResult(resolveGitAnalysis(group, errorType))
            );
        });
    }

    public int analyseFramesAndAssign(Map<String, Map<String, ErrorGroup>> bucketedGroups, String stackTrace) {
        String normalizedTrace = stackTrace
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"");

        List<ParsedFrame> frames = stackTraceParser.parseAllFrames(normalizedTrace);
        int framesAnalyzed = frames.size();

        if(frames.isEmpty()) {
            log.warn("No frames found in provided stack trace. Skipping Git analysis.");
            GitAnalysisResult noFrames = GitAnalysisResult.builder()
                    .status("No frames found in the provided stack trace.")
                    .build();
            assignToAll(bucketedGroups, noFrames);
            return framesAnalyzed;
        }

        log.info("Running multi-frame Git analysis on {} frame(s).", framesAnalyzed);
        GitAnalysisResult result = gitAnalyser.analyseFrames(frames);

        assignToAll(bucketedGroups, result);
        return framesAnalyzed;
    }

    private void assignToAll(Map<String, Map<String, ErrorGroup>> bucketedGroups, GitAnalysisResult result) {
        bucketedGroups.forEach((timeWindow, errorGroups) -> {
            log.info("Assigning Git result to all groups in time window: {}", timeWindow);
            errorGroups.forEach((errorType, group) ->
                    group.setGitAnalysisResult(result)
            );
        });
    }

    private GitAnalysisResult resolveGitAnalysis(ErrorGroup group, String errorType) {
        try {
            log.info("Git analysis -> error type: '{}'", errorType);
            return gitAnalyser.analyse(group);
        } catch (Exception e) {
            log.error("Git analysis failed for '{}': {}", errorType, e.getMessage(), e);
            return GitAnalysisResult.builder()
                    .status("Git analysis failed: " + e.getMessage())
                    .build();
        }
    }
}