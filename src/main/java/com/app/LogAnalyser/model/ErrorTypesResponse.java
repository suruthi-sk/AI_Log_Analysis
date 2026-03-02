package com.app.LogAnalyser.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ErrorTypesResponse {

    private int totalEntries;
    private int uniqueErrorCount;
    private List<ErrorTypeInfo> errorTypes;
    private LocalDateTime analyzedAt;
}