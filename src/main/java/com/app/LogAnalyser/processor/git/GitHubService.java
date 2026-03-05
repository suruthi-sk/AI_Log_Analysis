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

import java.util.*;

@Slf4j
@Service
public class GitHubService implements GitProviderService {

    private static final String GITHUB_GRAPHQL_URL = "https://api.github.com/graphql";

    @Value("${git.github.owner:}")
    private String owner;

    @Value("${git.github.repo:}")
    private String repo;

    @Value("${git.github.branch:master}")
    private String branch;

    @Value("${git.github.token:}")
    private String token;

    private final RestTemplate restTemplate;
    private final StackTraceParser stackTraceParser;

    public GitHubService(RestTemplate restTemplate, StackTraceParser stackTraceParser) {
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
        log.info("GitHub blame analysis -> file: {} | line: {}", frame.filePath, frame.lineNumber);

        List<Map<String, Object>> ranges = fetchBlameRanges(frame.filePath);

        if(ranges == null) {
            return GitAnalysisResult.builder()
                    .className(frame.className)
                    .filePath(frame.filePath)
                    .errorLineNumber(frame.lineNumber)
                    .status("Failed to fetch blame from GitHub. Check your token and repo config.")
                    .build();
        }

        if(ranges.isEmpty()) {
            return GitAnalysisResult.builder()
                    .className(frame.className)
                    .filePath(frame.filePath)
                    .errorLineNumber(frame.lineNumber)
                    .status("No blame data found for this file.")
                    .build();
        }

        return analyseBlameRanges(ranges, frame);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchBlameRanges(String filePath) {

        String query = String.format("""
            {
              repository(owner: "%s", name: "%s") {
                ref(qualifiedName: "%s") {
                  target {
                    ... on Commit {
                      blame(path: "%s") {
                        ranges {
                          startingLine
                          endingLine
                          commit {
                            oid
                            message
                            committedDate
                            author {
                              name
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """, owner, repo, branch, filePath);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("query", query);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(GITHUB_GRAPHQL_URL, HttpMethod.POST, new HttpEntity<>(requestBody, buildHeaders()), Map.class);

            Map<String, Object> body = response.getBody();
            if(body == null)
                return null;

            Map<String, Object> data = (Map<String, Object>) body.get("data");
            Map<String, Object> repository = (Map<String, Object>) data.get("repository");
            Map<String, Object> ref = (Map<String, Object>) repository.get("ref");

            if(ref == null) {
                log.warn("GitHub blame: branch '{}' not found in repo", branch);
                return Collections.emptyList();
            }

            Map<String, Object> target = (Map<String, Object>) ref.get("target");
            Map<String, Object> blame = (Map<String, Object>) target.get("blame");
            List<Map<String, Object>> ranges = (List<Map<String, Object>>) blame.get("ranges");

            log.info("GitHub blame: got {} ranges for file '{}'", ranges.size(), filePath);
            return ranges;

        } catch (Exception e) {
            log.error("GitHub blame API call failed for '{}': {}", filePath, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private GitAnalysisResult analyseBlameRanges(List<Map<String, Object>> ranges, ParsedFrame frame) {

        for(Map<String, Object> range : ranges) {
            int startingLine = (int) range.get("startingLine");
            int endingLine   = (int) range.get("endingLine");

            if(frame.lineNumber >= startingLine && frame.lineNumber <= endingLine) {
                GitCommitInfo commitInfo = extractCommitInfo(range);

                log.info("Level 1 hit — error line {} in range {}-{} | commit: {}", frame.lineNumber, startingLine, endingLine, commitInfo.getShortSha());

                return GitAnalysisResult.builder()
                        .className(frame.className)
                        .filePath(frame.filePath)
                        .errorLineNumber(frame.lineNumber)
                        .errorLineTouched(true)
                        .errorLineCommit(commitInfo)
                        .fileHasRecentChanges(true)
                        .status("Error line " + frame.lineNumber + " was last changed by " + commitInfo.getAuthor() + " on " + commitInfo.getDate() + " — this may be the cause.")
                        .build();
            }
        }

        log.info("Level 1 miss — error line {} not in any range. Falling back to Level 2 (full file scan).", frame.lineNumber);

        GitCommitInfo latestFileCommit = null;
        List<String> changedLineRanges = new ArrayList<>();
        String latestDate = null;

        for(Map<String, Object> range : ranges) {
            int startingLine = (int) range.get("startingLine");
            int endingLine = (int) range.get("endingLine");
            GitCommitInfo commitInfo = extractCommitInfo(range);

            changedLineRanges.add("lines " + startingLine + " - " + endingLine + " (last changed by " + commitInfo.getAuthor() + " on " + commitInfo.getDate() + ")");

            if(latestDate == null || commitInfo.getDate().compareTo(latestDate) > 0) {
                latestDate = commitInfo.getDate();
                latestFileCommit = commitInfo;
            }
        }

        if(latestFileCommit != null) {
            log.info("Level 2 hit — latest change in file by {} on {}", latestFileCommit.getAuthor(), latestFileCommit.getDate());

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

    @SuppressWarnings("unchecked")
    private GitCommitInfo extractCommitInfo(Map<String, Object> range) {
        Map<String, Object> commit = (Map<String, Object>) range.get("commit");

        Map<String, Object> author = (Map<String, Object>) commit.get("author");
        String oid = (String) commit.get("oid");
        String message = (String) commit.get("message");
        String committedDate = (String) commit.get("committedDate");
        String authorName = (String) author.get("name");

        return GitCommitInfo.builder()
                .sha(oid)
                .shortSha(oid.substring(0, 7))
                .message(firstLine(message))
                .author(authorName)
                .date(committedDate)
                .build();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private String firstLine(String text) {
        if (text == null) return "";
        return text.split("\n")[0].trim();
    }
}