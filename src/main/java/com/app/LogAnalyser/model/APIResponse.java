package com.app.LogAnalyser.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private ApiError error;
    private LocalDateTime timestamp;

    public static <T> APIResponse<T> ok(T data) {
        return APIResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> APIResponse<T> ok(T data, String message) {
        return APIResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> APIResponse<T> fail(ErrorCode code, String detail) {
        return APIResponse.<T>builder()
                .success(false)
                .error(ApiError.of(code, detail))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> APIResponse<T> fail(ErrorCode code, String detail, List<String> validationErrors) {
        return APIResponse.<T>builder()
                .success(false)
                .error(ApiError.of(code, detail, validationErrors))
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiError {
        private String code;
        private String message;
        private List<String> validationErrors;

        public static ApiError of(ErrorCode code, String message) {
            return ApiError.builder()
                    .code(code.name())
                    .message(message)
                    .build();
        }

        public static ApiError of(ErrorCode code, String message, List<String> validationErrors) {
            return ApiError.builder()
                    .code(code.name())
                    .message(message)
                    .validationErrors(validationErrors)
                    .build();
        }
    }

    public enum ErrorCode {
        VALIDATION_FAILED,
        UNKNOWN_PARAMETER,
        EMPTY_FILE,
        INVALID_FILE_TYPE,
        FILE_TOO_LARGE,
        INVALID_DATE_FORMAT,
        INVALID_TIME_FORMAT,
        INVALID_TIME_RANGE,
        INVALID_PARAMETER,
        PARSE_ERROR,
        INTERNAL_ERROR
    }
}