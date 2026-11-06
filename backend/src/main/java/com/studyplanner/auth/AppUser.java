package com.studyplanner.auth;

import java.time.Instant;
import java.util.UUID;

/** Internal account row. Never serialize passwordHash in an API response. */
public record AppUser(UUID id, String email, String passwordHash, Instant createdAt) {}
