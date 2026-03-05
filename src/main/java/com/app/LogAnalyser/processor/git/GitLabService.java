package com.app.LogAnalyser.processor.git;

import com.app.LogAnalyser.model.GitAnalysisResult;
import com.app.LogAnalyser.model.GitCommitInfo;
import com.app.LogAnalyser.processor.StackTraceParser;
import com.app.LogAnalyser.processor.StackTraceParser.ParsedFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
public class GitLabService implements GitProviderService {

    @Value("${git.gitlab.host:https://gitlab.com}")
    private String gitlabHost;

    @Value("${git.gitlab.project:}")
    private String project;

    @Value("${git.gitlab.branch:main}")
    private String branch;

    @Value("${git.gitlab.token:}")
    private String token;

    private final RestTemplate restTemplate;
    private final StackTraceParser stackTraceParser;

    public GitLabService(RestTemplate restTemplate, StackTraceParser stackTraceParser) {
        this.restTemplate = restTemplate;
        this.stackTraceParser = stackTraceParser;
    }

    @Override
    public GitAnalysisResult analyse(String stackTrace) {
        ParsedFrame frame = stackTraceParser.parse(stackTrace);

        if(frame == null) {
            return GitAnalysisResult.builder()
                    .status("Could not extract a valid frame from stack trace.")
                    .build();
        }

        log.info("GitLab blame analysis → file: {} | line: {}", frame.filePath, frame.lineNumber);

        List<Map<String, Object>> blameBlocks = fetchBlameBlocks(frame.filePath);

        if (blameBlocks == null) {
            return GitAnalysisResult.builder()
                    .className(frame.className)
                    .filePath(frame.filePath)
                    .errorLineNumber(frame.lineNumber)
                    .status("Failed to fetch blame from GitLab. Check your token and project config.")
                    .build();
        }

        if (blameBlocks.isEmpty()) {
            return GitAnalysisResult.builder()
                    .className(frame.className)
                    .filePath(frame.filePath)
                    .errorLineNumber(frame.lineNumber)
                    .status("No blame data found for this file.")
                    .build();
        }

        return analyseBlameBlocks(blameBlocks, frame);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchBlameBlocks(String filePath) {

        String encodedProject = URLEncoder.encode(project, StandardCharsets.UTF_8);
        String encodedFilePath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);

        String url = String.format("%s/api/v4/projects/%s/repository/files/%s/blame?ref=%s", gitlabHost, encodedProject, encodedFilePath, branch);

        try {
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), List.class);

            List<Map<String, Object>> blocks = response.getBody();
            if(blocks == null)
                return Collections.emptyList();

            log.info("GitLab blame: got {} blocks for file '{}'", blocks.size(), filePath);
            return blocks;

        } catch (Exception e) {
            log.error("GitLab blame API call failed for '{}': {}", filePath, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private GitAnalysisResult analyseBlameBlocks(List<Map<String, Object>> blameBlocks, ParsedFrame frame) {
        GitCommitInfo errorLineCommit = null;
        GitCommitInfo latestFileCommit = null;
        List<String> changedLineRanges = new ArrayList<>();
        String latestDate = null;

        int currentLine = 1;

        for(Map<String, Object> block : blameBlocks) {
            Map<String, Object> commit = (Map<String, Object>) block.get("commit");
            List<String> lines = (List<String>) block.get("lines");

            if(commit == null || lines == null)
                continue;

            String id = (String) commit.get("id");
            String message = (String) commit.get("message");
            String committedDate = (String) commit.get("committed_date");
            String authorName = (String) commit.get("author_name");

            int startingLine = currentLine;
            int endingLine = currentLine + lines.size() - 1;

            currentLine = endingLine + 1;

            GitCommitInfo commitInfo = GitCommitInfo.builder()
                    .sha(id)
                    .shortSha(id.substring(0, 7))
                    .message(firstLine(message))
                    .author(authorName)
                    .date(committedDate)
                    .build();

            if(frame.lineNumber >= startingLine && frame.lineNumber <= endingLine) {
                log.info("Level 1 hit — error line {} is in range {}-{} | commit: {}", frame.lineNumber, startingLine, endingLine, id.substring(0, 7));
                errorLineCommit = commitInfo;
            }

            changedLineRanges.add("lines " + startingLine + " - " + endingLine + " (last changed by " + authorName + " on " + committedDate + ")");

            if(latestDate == null || committedDate.compareTo(latestDate) > 0) {
                latestDate = committedDate;
                latestFileCommit = commitInfo;
            }
        }

        if(errorLineCommit != null) {
            return GitAnalysisResult.builder()
                    .className(frame.className)
                    .filePath(frame.filePath)
                    .errorLineNumber(frame.lineNumber)
                    .errorLineTouched(true)
                    .errorLineCommit(errorLineCommit)
                    .fileHasRecentChanges(true)
                    .changedLineRanges(changedLineRanges)
                    .latestFileCommit(latestFileCommit)
                    .status("Error line " + frame.lineNumber + " was last changed by " + errorLineCommit.getAuthor() + " on " + errorLineCommit.getDate() + " — this may be the cause.")
                    .build();
        }

        if(latestFileCommit != null) {
            return GitAnalysisResult.builder()
                    .className(frame.className)
                    .filePath(frame.filePath)
                    .errorLineNumber(frame.lineNumber)
                    .errorLineTouched(false)
                    .fileHasRecentChanges(true)
                    .changedLineRanges(changedLineRanges)
                    .latestFileCommit(latestFileCommit)
                    .status("Error line " + frame.lineNumber + " was not recently changed, " + "but the file has other changes. Latest change by " + latestFileCommit.getAuthor() + " on " + latestFileCommit.getDate() + ".")
                    .build();
        }

        return GitAnalysisResult.builder()
                .className(frame.className)
                .filePath(frame.filePath)
                .errorLineNumber(frame.lineNumber)
                .errorLineTouched(false)
                .fileHasRecentChanges(false)
                .changedLineRanges(Collections.emptyList())
                .status("No recent changes found in this file.")
                .build();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String firstLine(String text) {
        if (text == null) return "";
        return text.split("\n")[0].trim();
    }
}