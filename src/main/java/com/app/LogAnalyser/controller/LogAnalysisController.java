package com.app.LogAnalyser.controller;

import com.app.LogAnalyser.model.AnalysisResponse;
import com.app.LogAnalyser.service.LogAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api")
public class LogAnalysisController {

    private final LogAnalysisService logAnalysisService;

    public LogAnalysisController(LogAnalysisService logAnalysisService) {
        this.logAnalysisService = logAnalysisService;
    }

    @PostMapping("/log/analyze")
    public ResponseEntity<AnalysisResponse> analyze( @RequestParam("file") MultipartFile file, @RequestParam(value = "date", required = false) String date, @RequestParam(value = "fromTime", required = false) String fromTime, @RequestParam(value = "toTime", required = false) String toTime, @RequestParam(value = "timeWindow", defaultValue = "5")  int timeWindowMinutes, @RequestParam(value = "spikeLimit", defaultValue = "3")  int spikeLimit, @RequestParam(value = "levels", defaultValue = "ERROR,WARN") String levels) {
        log.info("Received file: {} | date: {} | from: {} | to: {} | window: {}min | levels: {}",file.getOriginalFilename(), date, fromTime, toTime, timeWindowMinutes, levels);

        if(file.isEmpty()) {
            log.warn("Empty file received");
            return ResponseEntity.badRequest().build();
        }

        try {
            AnalysisResponse response = logAnalysisService.analyze(file.getInputStream(), date, fromTime, toTime, timeWindowMinutes, spikeLimit, levels);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException", e);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("IOException", e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Log Analyser is running!");
    }
}