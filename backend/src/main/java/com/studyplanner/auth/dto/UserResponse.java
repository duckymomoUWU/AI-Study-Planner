package com.studyplanner.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** Public view of an account. Never carries the password hash. */
public record UserResponse(UUID id, String email, Instant createdAt) {}
