package com.app.LogAnalyser.controller;

import com.app.LogAnalyser.model.AnalysisResponse;
import com.app.LogAnalyser.model.APIResponse;
import com.app.LogAnalyser.model.APIResponse.ErrorCode;
import com.app.LogAnalyser.model.ErrorTypesResponse;
import com.app.LogAnalyser.service.LogAnalysisService;
import com.app.LogAnalyser.validator.RequestValidator;
import com.app.LogAnalyser.validator.RequestValidator.ValidationResult;
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
    private final RequestValidator requestValidator;

    public LogAnalysisController(LogAnalysisService logAnalysisService, RequestValidator requestValidator) {
        this.logAnalysisService = logAnalysisService;
        this.requestValidator   = requestValidator;
    }

    @PostMapping("/log/getAllErrors")
    public ResponseEntity<APIResponse<ErrorTypesResponse>> getErrorTypes(@RequestParam("file") MultipartFile file, @RequestParam(value = "date", required = false) String date, @RequestParam(value = "fromTime", required = false) String fromTime, @RequestParam(value = "toTime",   required = false) String toTime, @RequestParam(value = "levels",   defaultValue = "ERROR,WARN") String levels) {

        log.info("GET /errors | file: {} | date: {} | from: {} | to: {} | levels: {}", file.getOriginalFilename(), date, fromTime, toTime, levels);

        ValidationResult validation = requestValidator.validateGetErrors(file, date, fromTime, toTime, levels);

        if(!validation.isValid()) {
            return ResponseEntity.badRequest()
                    .body(APIResponse.fail(ErrorCode.VALIDATION_FAILED, "Request validation failed.", validation.getErrors()));
        }

        try {
            ErrorTypesResponse response = logAnalysisService.getErrorTypes(file.getInputStream(), date, fromTime, toTime, levels);
            return ResponseEntity.ok(APIResponse.ok(response, "Found " + response.getUniqueErrorCount() + " unique error type(s)."));
        } catch (IOException e) {
            log.error("IO error reading file for /errors", e);
            return ResponseEntity.internalServerError()
                    .body(APIResponse.fail(ErrorCode.PARSE_ERROR, "Failed to read the uploaded file. Please ensure it is not corrupted."));
        } catch (Exception e) {
            log.error("Unexpected error in /errors", e);
            return ResponseEntity.internalServerError()
                    .body(APIResponse.fail(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Please try again."));
        }
    }

    @PostMapping("/log/analyze")
    public ResponseEntity<APIResponse<AnalysisResponse>> analyze(@RequestParam("file") MultipartFile file, @RequestParam(value = "date", required = false) String date, @RequestParam(value = "fromTime", required = false) String fromTime, @RequestParam(value = "toTime", required = false) String toTime, @RequestParam(value = "timeWindow", defaultValue = "0") int timeWindowMinutes, @RequestParam(value = "spikeLimit", defaultValue = "3") int spikeLimit, @RequestParam(value = "levels", defaultValue = "ERROR,WARN") String levels, @RequestParam(value = "errorTypes", required = false) String errorTypes) {

        log.info("POST /analyze | file: {} | date: {} | from: {} | to: {} | window: {}min | spikeLimit: {} | levels: {} | errorTypes: '{}'", file.getOriginalFilename(), date, fromTime, toTime, timeWindowMinutes, spikeLimit, levels, errorTypes);

        ValidationResult validation = requestValidator.validateAnalyze(file, date, fromTime, toTime, timeWindowMinutes, spikeLimit, levels, errorTypes);

        if (!validation.isValid()) {
            return ResponseEntity.badRequest()
                    .body(APIResponse.fail(ErrorCode.VALIDATION_FAILED, "Request validation failed. Please fix the errors and try again.", validation.getErrors()));
        }

        try {
            AnalysisResponse response = logAnalysisService.analyze(file.getInputStream(), date, fromTime, toTime, timeWindowMinutes, spikeLimit, levels, errorTypes);
            return ResponseEntity.ok(APIResponse.ok(response, "Log analysis completed successfully."));

        } catch (IOException e) {
            log.error("IO error reading file for /analyze", e);
            return ResponseEntity.internalServerError()
                    .body(APIResponse.fail(ErrorCode.PARSE_ERROR, "Failed to read the uploaded file. Please ensure it is not corrupted."));

        } catch (Exception e) {
            log.error("Unexpected error in /analyze", e);
            return ResponseEntity.internalServerError().body(APIResponse.fail(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Please try again or contact support."));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<APIResponse<String>> health() {
        return ResponseEntity.ok(APIResponse.ok("Log Analyser is running!"));
    }
}