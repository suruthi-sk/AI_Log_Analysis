package com.app.LogAnalyser.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitAnalysisResult {
    private String className;
    private String filePath;
    private int errorLineNumber;
    private boolean errorLineTouched;
    private GitCommitInfo errorLineCommit;
    private boolean fileHasRecentChanges;
    private List<String> changedLineRanges;
    private GitCommitInfo latestFileCommit;
    private String status;
}