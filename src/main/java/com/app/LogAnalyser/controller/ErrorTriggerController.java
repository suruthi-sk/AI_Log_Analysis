package com.app.LogAnalyser.controller;

import com.app.LogAnalyser.service.ErrorTriggerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/trigger")
public class ErrorTriggerController {

    private final ErrorTriggerService errorTriggerService;

    public ErrorTriggerController(ErrorTriggerService errorTriggerService) {
        this.errorTriggerService = errorTriggerService;
    }

    @GetMapping("/null-pointer")
    public ResponseEntity<String> triggerNullPointer() {
        errorTriggerService.generateNullPointer();
        return ResponseEntity.ok("Done");
    }

    @GetMapping("/array-out-of-bounds")
    public ResponseEntity<String> triggerArrayOutOfBounds() {
        errorTriggerService.generateArrayOutOfBounds();
        return ResponseEntity.ok("Done");
    }

    @GetMapping("/number-format")
    public ResponseEntity<String> triggerNumberFormat() {
        errorTriggerService.generateNumberFormat();
        return ResponseEntity.ok("Done");
    }

    @GetMapping("/file-not-found")
    public ResponseEntity<String> triggerFileNotFound() {
        errorTriggerService.generateFileNotFound();
        return ResponseEntity.ok("Done");
    }

    @GetMapping("/class-cast")
    public ResponseEntity<String> triggerClassCast() {
        errorTriggerService.generateClassCast();
        return ResponseEntity.ok("Done");
    }

    @GetMapping("/illegal-argument")
    public ResponseEntity<String> triggerIllegalArgument() {
        errorTriggerService.generateIllegalArgument();
        return ResponseEntity.ok("Done");
    }

    @GetMapping("/stack-overflow")
    public ResponseEntity<String> triggerStackOverflow() {
        errorTriggerService.generateStackOverflow();
        return ResponseEntity.ok("Done");
    }

    @GetMapping("/concurrent-modification")
    public ResponseEntity<String> triggerConcurrentModification() {
        errorTriggerService.generateConcurrentModification();
        return ResponseEntity.ok("Done");
    }

    @GetMapping("/all")
    public ResponseEntity<Map<String, String>> triggerAll() {
        return ResponseEntity.ok(errorTriggerService.generateAll());
    }
}
