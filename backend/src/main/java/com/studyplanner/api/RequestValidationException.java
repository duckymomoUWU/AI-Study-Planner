package com.studyplanner.api;

import java.util.List;

/** Thrown for invalid request input that is not specific to one feature (for example page or limit). */
public class RequestValidationException extends RuntimeException {
    private final transient List<ApiErrorResponse.ErrorDetail> details;

    public RequestValidationException(List<ApiErrorResponse.ErrorDetail> details) {
        super("Request validation failed");
        this.details = List.copyOf(details);
    }

    public List<ApiErrorResponse.ErrorDetail> details() {
        return details;
    }
}
