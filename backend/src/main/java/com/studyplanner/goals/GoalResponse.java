package com.studyplanner.goals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a goal (inside the "data" envelope). Never exposes the owner ID. */
public record GoalResponse(UUID id, String subject, String topics, LocalDate startDate, LocalDate examDate,
                           int minutesPerDay, String timezone, int version, Instant createdAt) {
    static GoalResponse from(GoalJdbcRepository.StoredGoal g) {
        return new GoalResponse(g.id(), g.subject(), g.topics(), g.startDate(), g.examDate(),
                g.minutesPerDay(), g.timezone(), g.version(), g.createdAt());
    }
}
