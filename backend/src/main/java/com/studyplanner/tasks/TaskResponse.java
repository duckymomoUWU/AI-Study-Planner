package com.studyplanner.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a task (inside the "data" envelope). */
public record TaskResponse(UUID id, UUID goalId, String title, LocalDate scheduledDate, int estimatedMinutes,
                           boolean completed, String source, Instant createdAt) {
    public static TaskResponse from(TaskJdbcRepository.StoredTask t) {
        return new TaskResponse(t.id(), t.goalId(), t.title(), t.scheduledDate(), t.estimatedMinutes(),
                t.completed(), t.source(), t.createdAt());
    }
}
