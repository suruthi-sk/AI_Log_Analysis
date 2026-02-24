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

    @Value("${ollama.api.url:mock}")
    private String apiUrl;

    @Value("${ollama.model:llama3.2}")
    private String model;

    private final RestTemplate restTemplate;

    private final Map<String, AiSummary> summaryCache = new ConcurrentHashMap<>();

    public AiSummaryGenerator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void generateAndAssign(Map<String, Map<String, ErrorGroup>> bucketedGroups) {

        bucketedGroups.forEach((timeWindow, errorGroups) -> {

            log.info("Processing time window: {}", timeWindow);

            errorGroups.forEach((errorType, group) -> {

                String cacheKey = errorType;

                if(summaryCache.containsKey(cacheKey)) {
                    log.info("Cache hit for: {} in window: {}", errorType, timeWindow);
                    group.setAiSummary(summaryCache.get(cacheKey));
                    return;
                }

                if(apiUrl == null || apiUrl.equals("mock")) {
                    log.warn("No Ollama URL. Mock summary for: {}", errorType);
                    AiSummary mock = buildMockSummary(group);
                    summaryCache.put(cacheKey, mock);
                    group.setAiSummary(mock);
                    return;
                }

                try {
                    log.info("Calling Ollama for: {} in window: {}", errorType, timeWindow);
                    String prompt   = buildPrompt(group);
                    String response = callOllama(prompt);
                    AiSummary summary = parseResponse(response, group);

                    summaryCache.put(cacheKey, summary);
                    group.setAiSummary(summary);

                } catch(Exception e) {
                    log.error("Ollama failed for: {}. Reason: {}", errorType, e.getMessage());
                    group.setAiSummary(AiSummary.fallback(errorType));
                }
            });
        });
    }

    // Prompt now includes time window context
    private String buildPrompt(ErrorGroup group) {
        return "You are a backend systems reliability engineer.\n" +
                "Analyze the error below. Reply in EXACTLY 3 lines. No extra text.\n" +
                "Line 1 must start with SUMMARY:\n" +
                "Line 2 must start with CAUSE:\n" +
                "Line 3 must start with FIX:\n" +
                "No markdown. No bold. No bullet points.\n\n" +
                group.toStructuredSummary();
    }

    private String callOllama(String prompt) {
        String url = apiUrl;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url, request, Map.class);

        return response.getBody().get("response").toString();
    }

    private AiSummary parseResponse(String response, ErrorGroup group) {
        String[] lines = response.split("\\n");

        String problemSummary = "N/A";
        String rootCause      = "N/A";
        String suggestedFix   = "N/A";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("SUMMARY:")) {
                problemSummary = trimmed.substring("SUMMARY:".length()).trim();
            } else if (trimmed.toUpperCase().startsWith("CAUSE:")) {
                rootCause = trimmed.substring("CAUSE:".length()).trim();
            } else if (trimmed.toUpperCase().startsWith("FIX:")) {
                suggestedFix = trimmed.substring("FIX:".length()).trim();
            }
        }

        if (problemSummary.equals("N/A") && rootCause.equals("N/A")) {
            log.warn("Could not parse response for {}. Using fallback.",
                    group.getErrorType());
            return AiSummary.fallback(group.getErrorType());
        }

        return AiSummary.builder()
                .problemSummary(problemSummary)
                .rootCause(rootCause)
                .suggestedFix(suggestedFix)
                .mockFallback(false)
                .build();
    }

    private AiSummary buildMockSummary(ErrorGroup group) {
        return AiSummary.builder()
                .problemSummary("Multiple " + group.getErrorType() +
                        " errors detected in window " + group.getTimeWindow())
                .rootCause("Possible service instability causing repeated failures.")
                .suggestedFix("Check service logs and review recent deployments.")
                .mockFallback(true)
                .build();
    }
}