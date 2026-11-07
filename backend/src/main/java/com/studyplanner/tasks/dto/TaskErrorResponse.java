package com.studyplanner.tasks.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskErrorResponse(
        ErrorDetail error,
        String code,
        String message,
        List<?> fieldErrors
) {
    public record ErrorDetail(
            String code,
            String message,
            Object details
    ) {}

    public static TaskErrorResponse of(String code, String message, Object details) {
        return new TaskErrorResponse(
                new ErrorDetail(code, message, details),
                code,
                message,
                details instanceof List<?> list ? list : null
        );
    }

    public static TaskErrorResponse of(String code, String message) {
        return of(code, message, null);
    }
}
