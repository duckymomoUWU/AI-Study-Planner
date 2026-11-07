package com.studyplanner.auth.dto;

public record CsrfResponse(
        String headerName,
        String parameterName,
        String token
) {}
