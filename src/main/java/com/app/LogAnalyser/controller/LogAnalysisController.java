package com.app.LogAnalyser.controller;

import com.app.LogAnalyser.model.AnalysisResponse;
import com.app.LogAnalyser.service.LogAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api")
public class LogAnalysisController {

    private final LogAnalysisService logAnalysisService;

    public LogAnalysisController(LogAnalysisService logAnalysisService) {
        this.logAnalysisService = logAnalysisService;
    }


    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(@RequestParam("file") MultipartFile file) {

        log.info("Received file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            log.warn("Empty file received");
            return ResponseEntity.badRequest().build();
        }

        try {
            String logContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            AnalysisResponse response = logAnalysisService.analyze(logContent);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Failed to read uploaded file: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Log Analyser is running!");
    }
}