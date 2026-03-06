package com.app.LogAnalyser.processor.git;

import com.app.LogAnalyser.model.ErrorGroup;
import com.app.LogAnalyser.model.GitAnalysisResult;
import com.app.LogAnalyser.model.GitCommitInfo;
import com.app.LogAnalyser.processor.StackTraceParser;
import com.app.LogAnalyser.processor.StackTraceParser.ParsedFrame;
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

    public GitAnalysisResult analyseFrames(List<ParsedFrame> frames) {
        if(frames == null || frames.isEmpty()) {
            log.warn("No frames provided for Git analysis.");
            return GitAnalysisResult.builder()
                    .status("No frames found in stack trace.")
                    .build();
        }

        log.info("Running Git analysis on {} frame(s).", frames.size());

        GitAnalysisResult mostRecent = null;
        String latestDate = null;

        for(ParsedFrame frame : frames) {
            GitAnalysisResult result = analyseFrame(frame);

            if(result == null)
                continue;

            String commitDate = resolveCommitDate(result);

            if(commitDate == null) {
                log.debug("No commit date found for frame: {} — skipping for most-recent comparison.", frame.className);
                if(mostRecent == null)
                    mostRecent = result;
                continue;
            }

            if(latestDate == null || commitDate.compareTo(latestDate) > 0) {
                latestDate = commitDate;
                mostRecent = result;
                log.info("New most-recent frame -> class: {} | date: {}", frame.className, commitDate);
            }
        }

        if(mostRecent == null) {
            log.warn("No Git results could be resolved across {} frame(s).", frames.size());
            return GitAnalysisResult.builder()
                    .status("Git analysis returned no results for any frame.")
                    .build();
        }

        log.info("Git analysis complete. Most recent commit date: {}", latestDate);
        return mostRecent;
    }

    private GitAnalysisResult analyseFrame(ParsedFrame frame) {
        try {
            log.info("Analysing frame -> class: {} | line: {}", frame.className, frame.lineNumber);
            String syntheticTrace = "at " + frame.className + ".method(" + frame.filePath.substring(frame.filePath.lastIndexOf('/') + 1) + ":" + frame.lineNumber + ")";
            return gitServiceFactory.getProvider().analyse(syntheticTrace);
        } catch (Exception e) {
            log.error("Git analysis failed for frame '{}': {}", frame.className, e.getMessage());
            return null;
        }
    }

    private String resolveCommitDate(GitAnalysisResult result) {
        if(result.getErrorLineCommit() != null && result.getErrorLineCommit().getDate() != null)
            return result.getErrorLineCommit().getDate();

        if(result.getLatestFileCommit() != null && result.getLatestFileCommit().getDate() != null)
            return result.getLatestFileCommit().getDate();

        return null;
    }

    private String extractStackTrace(ErrorGroup errorGroup) {
        Map<String, List<String>> metadata = errorGroup.getAggregatedMetadata();
        if(metadata == null)
            return null;

        List<String> traces = metadata.get("stackTrace");
        if(traces == null || traces.isEmpty())
            return null;

        return traces.get(0);
    }
}