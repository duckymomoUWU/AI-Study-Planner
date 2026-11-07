package com.studyplanner.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Error envelope: {"error": {"code", "message", "details": [{"field", "issue", "message"}]}}. */
public record ApiErrorResponse(ApiError error) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(String code, String message, List<ErrorDetail> details) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(String field, String issue, String message) {
        public ErrorDetail(String field, String issue) {
            this(field, issue, null);
        }
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ApiError(code, message, null));
    }

    public static ApiErrorResponse of(String code, String message, List<ErrorDetail> details) {
        return new ApiErrorResponse(new ApiError(code, message, details));
    }
}
