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

    @Value("${ollama.model:gemma3:1b}")
    private String model;

    private final RestTemplate restTemplate;

    private final Map<String, AiSummary> summaryCache = new ConcurrentHashMap<>();

    public AiSummaryGenerator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<AiSummary> generateAll(List<ErrorGroup> groups) {

        List<AiSummary> summaries = new ArrayList<>();

        for(ErrorGroup group : groups) {

            String cacheKey = group.getErrorType();

            if(summaryCache.containsKey(cacheKey)) {
                log.info("Cache hit! Reusing summary for: {}", cacheKey);
                summaries.add(summaryCache.get(cacheKey));
                continue;
            }

            if(apiUrl == null || apiUrl.equals("mock")) {
                log.warn("No Ollama URL configured. Returning mock for: {}", cacheKey);
                summaries.add(buildMockSummary(group));
                continue;
            }

            try {
                log.info("Calling Ollama for: {}", cacheKey);
                String prompt = createPrompt(group);
                String aiResponse = callOllama(prompt);
                AiSummary summary = parseSingleResponse(aiResponse, group);

                summaryCache.put(cacheKey, summary);
                summaries.add(summary);

            } catch(Exception e) {
                log.error("Ollama call failed for {}. Reason: {}", cacheKey, e.getMessage());
                summaries.add(AiSummary.fallback(group.getErrorType()));
            }
        }

        return summaries;
    }

    private String createPrompt(ErrorGroup group) {
        return "You are a software debug engineer.\n" +
                "Analyze the error below. Reply in EXACTLY 3 lines. No extra text.\n" +
                "Line 1 must start with SUMMARY:\n" +
                "Line 2 must start with CAUSE:\n" +
                "Line 3 must start with FIX:\n" +
                "No markdown. No bold. No bullet points.\n\n" +
                group.toStructuredSummary();
    }

    private String callOllama(String prompt) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);
        System.out.println(response);

        return response.getBody().get("response").toString();
    }

    private AiSummary parseSingleResponse(String response, ErrorGroup group) {

        String[] lines = response.split("\\n");

        String problemSummary = "N/A";
        String rootCause = "N/A";
        String suggestedFix = "N/A";

        for(String line : lines) {
            String trimmed = line.trim();
            if(trimmed.toUpperCase().startsWith("SUMMARY:")) {
                problemSummary = trimmed.substring("SUMMARY:".length()).trim();
            } else if(trimmed.toUpperCase().startsWith("CAUSE:")) {
                rootCause = trimmed.substring("CAUSE:".length()).trim();
            } else if(trimmed.toUpperCase().startsWith("FIX:")) {
                suggestedFix = trimmed.substring("FIX:".length()).trim();
            }
        }

        if(problemSummary.equals("N/A") && rootCause.equals("N/A")) {
            log.warn("Could not parse Ollama response for {}. Using fallback.", group.getErrorType());
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
                .problemSummary("Multiple " + group.getErrorType() + " errors detected in window " + group.getTimeWindow())
                .rootCause("Possible service instability causing repeated failures.")
                .suggestedFix("Check service logs and review recent deployments.")
                .mockFallback(true)
                .build();
    }
}
