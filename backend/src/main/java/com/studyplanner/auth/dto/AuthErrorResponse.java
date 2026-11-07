package com.studyplanner.auth.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthErrorResponse(
        ErrorDetail error
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(
            String code,
            String message,
            List<FieldErrorItem> details
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldErrorItem(
            String field,
            String issue,
            String message
    ) {
        public FieldErrorItem(String field, String issue) {
            this(field, issue, null);
        }
    }

    public AuthErrorResponse(String code, String message) {
        this(new ErrorDetail(code, message, List.of()));
    }

    public AuthErrorResponse(String code, String message, List<FieldErrorItem> details) {
        this(new ErrorDetail(code, message, details != null ? details : List.of()));
    }
}
