package com.app.LogAnalyser.processor;

import com.app.LogAnalyser.model.AiSummary;
import com.app.LogAnalyser.model.ErrorGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class AiSummaryGenerator {

    @Value("${ollama.api.url:mock}")
    private String apiUrl;

    @Value("${ollama.model:llama3.2}")
    private String model;

    @Value("${redis.cache.ai-summary.ttl-hours:24}")
    private long cacheTtlHours;

    private static final String CACHE_KEY_PREFIX = "ai_summary:";

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, AiSummary> redisTemplate;

    public AiSummaryGenerator(RestTemplate restTemplate, RedisTemplate<String, AiSummary> redisTemplate) {
        this.restTemplate  = restTemplate;
        this.redisTemplate = redisTemplate;
    }

    public void generateAndAssign(Map<String, Map<String, ErrorGroup>> bucketedGroups) {

        bucketedGroups.forEach((timeWindow, errorGroups) -> {

            log.info("Processing time window: {}", timeWindow);

            errorGroups.forEach((errorType, group) -> {

                String cacheKey = CACHE_KEY_PREFIX + errorType;
                AiSummary cached = getFromCache(cacheKey);
                if(cached != null) {
                    log.info("Redis cache hit for: '{}' in window: '{}'", errorType, timeWindow);
                    group.setAiSummary(cached);
                    return;
                }

                if(apiUrl == null || apiUrl.equals("mock")) {
                    log.warn("Ollama URL not configured. Using mock summary for: '{}'", errorType);
                    AiSummary mock = buildMockSummary(group);
                    group.setAiSummary(mock);
                    return;
                }

                try {
                    log.info("Calling Ollama for: '{}' in window: '{}'", errorType, timeWindow);
                    String prompt = buildPrompt(group);
                    String response = callOllama(prompt);
                    log.debug("Ollama raw response: {}", response);
                    AiSummary summary = parseResponse(response, group);

                    saveToCache(cacheKey, summary);
                    group.setAiSummary(summary);

                } catch (Exception e) {
                    log.error("Ollama call failed for '{}': {}", errorType, e.getMessage(), e);
                    group.setAiSummary(AiSummary.fallback(errorType));
                }
            });
        });
    }

    private AiSummary getFromCache(String cacheKey) {
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis GET failed for key '{}': {}. Proceeding without cache.", cacheKey, e.getMessage());
            return null;
        }
    }

    private void saveToCache(String cacheKey, AiSummary summary) {
        try {
            redisTemplate.opsForValue().set(cacheKey, summary, Duration.ofHours(cacheTtlHours));
            log.info("Cached AI summary in Redis: key='{}', ttl={}h", cacheKey, cacheTtlHours);
        } catch (Exception e) {
            log.warn("Redis SET failed for key '{}': {}. Summary will not be cached.", cacheKey, e.getMessage());
        }
    }


    private String buildPrompt(ErrorGroup group) {
        return """
            You are a software debug engineer.
            Output EXACTLY 3 lines.
            
            Format strictly:
            
            SUMMARY: <one sentence>
            CAUSE: <one sentence>
            FIX: <one sentence>
            
            No extra text.
            No markdown.
            No bullet points.Read the prompt carefully...
            
            Error:
            """ + group.toStructuredSummary();
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

        return response.getBody().get("response").toString();
    }


    private AiSummary parseResponse(String response, ErrorGroup group) {
        String problemSummary = "N/A";
        String rootCause      = "N/A";
        String suggestedFix   = "N/A";

        for (String line : response.split("\\n")) {
            String trimmed = line.trim();
            if      (trimmed.toUpperCase().startsWith("SUMMARY:"))
                problemSummary = trimmed.substring("SUMMARY:".length()).trim();
            else if (trimmed.toUpperCase().startsWith("CAUSE:"))
                rootCause      = trimmed.substring("CAUSE:".length()).trim();
            else if (trimmed.toUpperCase().startsWith("FIX:"))
                suggestedFix   = trimmed.substring("FIX:".length()).trim();
        }

        if (problemSummary.equals("N/A") && rootCause.equals("N/A")) {
            log.warn("Could not parse Ollama response for '{}'. Using fallback.", group.getErrorType());
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
                .problemSummary("Multiple " + group.getErrorType()
                        + " errors detected in window " + group.getTimeWindow())
                .rootCause("Possible service instability causing repeated failures.")
                .suggestedFix("Check service logs and review recent deployments.")
                .mockFallback(true)
                .build();
    }
}