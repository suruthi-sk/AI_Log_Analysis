package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.AiSummary;
import com.app.LogAnalyser.model.ErrorGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AiSummaryGenerator {

    @Value("${gemini.api.key:mock}")
    private String apiKey;

    @Value("${gemini.api.url:mock}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    // Cache: errorType → AiSummary
    private final Map<String, AiSummary> summaryCache = new ConcurrentHashMap<>();

    public AiSummaryGenerator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // -------------------------------------------------------
    // NEW METHOD — analyze all groups in ONE single API call
    // -------------------------------------------------------
    public List<AiSummary> generateAll(List<ErrorGroup> groups) {

        // If no API key, return mock for all groups
        if (apiKey == null || apiKey.equals("mock")) {
            log.warn("No Gemini API key. Returning mock summaries for all groups.");
            return groups.stream()
                    .map(this::buildMockSummary)
                    .toList();
        }

        try {
            // Build one combined prompt for ALL groups
            String combinedPrompt = buildCombinedPrompt(groups);
            log.info("Sending ONE combined Gemini call for {} groups", groups.size());

            String aiResponse = callGemini(combinedPrompt);
            return parseCombinedResponse(aiResponse, groups);

        } catch (Exception e) {
            log.error("Gemini combined call failed. Reason: {}", e.getMessage());
            // Fallback — return fallback summary for each group
            return groups.stream()
                    .map(g -> AiSummary.fallback(g.getErrorType()))
                    .toList();
        }
    }

    // Builds one prompt containing ALL groups
    private String buildCombinedPrompt(List<ErrorGroup> groups) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a backend systems reliability engineer.\n");
        prompt.append("Analyze each error group below and for EACH one respond in exactly 3 lines:\n");
        prompt.append("Line 1 - Problem Summary:\n");
        prompt.append("Line 2 - Root Cause:\n");
        prompt.append("Line 3 - Suggested Fix:\n");
        prompt.append("Separate each group's analysis with ---\n\n");

        for (int i = 0; i < groups.size(); i++) {
            prompt.append("Group ").append(i + 1).append(":\n");
            prompt.append(groups.get(i).toStructuredSummary());
            prompt.append("\n");
        }

        return prompt.toString();
    }

    // Parses the combined response — splits by --- separator
    private List<AiSummary> parseCombinedResponse(String response, List<ErrorGroup> groups) {
        List<AiSummary> summaries = new ArrayList<>();

        // Split the response by --- separator
        String[] sections = response.split("---");

        for (int i = 0; i < groups.size(); i++) {

            // If AI gave fewer sections than groups, use fallback
            if (i >= sections.length) {
                log.warn("Missing AI response for group {}. Using fallback.", groups.get(i).getErrorType());
                summaries.add(AiSummary.fallback(groups.get(i).getErrorType()));
                continue;
            }

            String section = sections[i].trim();
            String[] lines = section.split("\\n");

            String problemSummary = lines.length > 0 ?
                    lines[0].replaceAll("(?i).*summary[:\\-]?\\s*", "").trim() : "N/A";
            String rootCause = lines.length > 1 ?
                    lines[1].replaceAll("(?i).*cause[:\\-]?\\s*", "").trim() : "N/A";
            String suggestedFix = lines.length > 2 ?
                    lines[2].replaceAll("(?i).*fix[:\\-]?\\s*", "").trim() : "N/A";

            AiSummary summary = AiSummary.builder()
                    .problemSummary(problemSummary)
                    .rootCause(rootCause)
                    .suggestedFix(suggestedFix)
                    .mockFallback(false)
                    .build();

            // Store in cache for future use
            summaryCache.put(groups.get(i).getErrorType(), summary);
            summaries.add(summary);
        }

        return summaries;
    }

    private String callGemini(String prompt) {
        String url = apiUrl + "?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> text = new HashMap<>();
        text.put("text", prompt);

        Map<String, Object> part = new HashMap<>();
        part.put("parts", List.of(text));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(part));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        List<Map> candidates = (List<Map>) response.getBody().get("candidates");
        Map content = (Map) candidates.get(0).get("content");
        List<Map> parts = (List<Map>) content.get("parts");
        return parts.get(0).get("text").toString();
    }

    private AiSummary buildMockSummary(ErrorGroup group) {
        return AiSummary.builder()
                .problemSummary("Multiple " + group.getErrorType() +
                        " errors detected in window " + group.getTimeWindow())
                .rootCause("Possible service instability or misconfiguration causing repeated failures.")
                .suggestedFix("Check service logs, verify configurations, and review recent deployments.")
                .mockFallback(true)
                .build();
    }
}