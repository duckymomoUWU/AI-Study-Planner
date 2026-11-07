package com.studyplanner.auth.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthErrorResponse(
        String code,
        String message,
        List<FieldErrorItem> fieldErrors
) {
    public record FieldErrorItem(String field, String code, String message) {}

    public AuthErrorResponse(String code, String message) {
        this(code, message, null);
    }
}
