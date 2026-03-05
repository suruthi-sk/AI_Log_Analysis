package com.app.LogAnalyser.processor.git;

import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.GitAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class GitAnalysisProcessor {

    private final GitAnalyser gitAnalyser;

    public GitAnalysisProcessor(GitAnalyser gitAnalyser) {
        this.gitAnalyser = gitAnalyser;
    }

    public void analyseAndAssign(Map<String, Map<String, ErrorGroup>> bucketedGroups) {

        bucketedGroups.forEach((timeWindow, errorGroups) -> {

            log.info("Running Git analysis for time window: {}", timeWindow);

            errorGroups.forEach((errorType, group) ->
                    group.setGitAnalysisResult(resolveGitAnalysis(group, errorType))
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