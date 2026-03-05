package com.app.LogAnalyser.processor.git;

import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.GitAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GitAnalyser {

    private final GitServiceFactory gitServiceFactory;

    public GitAnalyser(GitServiceFactory gitServiceFactory) {
        this.gitServiceFactory = gitServiceFactory;
    }

    public GitAnalysisResult analyse(ErrorGroup errorGroup) {

        String stackTrace = extractStackTrace(errorGroup);

        if(stackTrace == null) {
            log.debug("No stack trace found for error type '{}'. Skipping Git analysis.", errorGroup.getErrorType());
            return GitAnalysisResult.builder()
                    .status("No stack trace available for this error.")
                    .build();
        }

        try {
            log.info("Running Git analysis for error type: '{}'", errorGroup.getErrorType());
            return gitServiceFactory.getProvider().analyse(stackTrace);
        } catch (Exception e) {
            log.error("Git analysis failed for error type '{}': {}", errorGroup.getErrorType(), e.getMessage());
            return GitAnalysisResult.builder()
                    .status("Git analysis failed: " + e.getMessage())
                    .build();
        }
    }

    private String extractStackTrace(ErrorGroup errorGroup) {
        Map<String, List<String>> metadata = errorGroup.getAggregatedMetadata();
        if(metadata == null)
            return null;

        List<String> traces = metadata.get("stackTrace");
        if (traces == null || traces.isEmpty())
            return null;

        return traces.get(0);
    }
}