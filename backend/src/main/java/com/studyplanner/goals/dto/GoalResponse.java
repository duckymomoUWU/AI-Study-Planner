package com.studyplanner.goals.dto;

import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoalResponse(
        UUID id,
        String subject,
        String topics,
        LocalDate startDate,
        LocalDate examDate,
        int minutesPerDay,
        String timezone,
        int version,
        GoalResponse data
) {
    public static GoalResponse of(UUID id, String subject, String topics, LocalDate startDate,
                                  LocalDate examDate, int minutesPerDay, String timezone, int version) {
        GoalResponse inner = new GoalResponse(id, subject, topics, startDate, examDate, minutesPerDay, timezone, version, null);
        return new GoalResponse(id, subject, topics, startDate, examDate, minutesPerDay, timezone, version, inner);
    }
}
