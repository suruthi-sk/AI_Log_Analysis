package com.app.LogAnalyser.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitCommitInfo {
    private String sha;
    private String shortSha;
    private String message;
    private String author;
    private String date;
}
