package com.app.LogAnalyser.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorTypeInfo {
    private String errorType;
    private int count;
    private String message;
}