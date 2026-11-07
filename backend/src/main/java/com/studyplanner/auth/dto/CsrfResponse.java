package com.studyplanner.auth.dto;

/** Body of GET /api/v1/auth/csrf (inside the "data" envelope). */
public record CsrfResponse(String token, String headerName) {}
