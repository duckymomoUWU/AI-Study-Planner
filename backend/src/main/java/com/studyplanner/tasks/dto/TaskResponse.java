package com.studyplanner.tasks.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResponse(
        UUID id,
        UUID goalId,
        String goalSubject,
        String title,
        LocalDate scheduledDate,
        int estimatedMinutes,
        boolean completed,
        String source,
        Instant createdAt,
        String subject,
        String date,
        Double durationHours,
        Double startHour,
        String type,
        String topics
) {
    public TaskResponse(UUID id, UUID goalId, String goalSubject, String title,
                        LocalDate scheduledDate, int estimatedMinutes, boolean completed,
                        String source, Instant createdAt) {
        this(
                id,
                goalId,
                goalSubject,
                title,
                scheduledDate,
                estimatedMinutes,
                completed,
                source,
                createdAt,
                goalSubject,
                scheduledDate != null ? scheduledDate.toString() : null,
                Math.round(((double) estimatedMinutes / 60.0) * 10.0) / 10.0,
                10.0,
                "app",
                null
        );
    }

    public TaskResponse(UUID id, UUID goalId, String goalSubject, String title,
                        LocalDate scheduledDate, int estimatedMinutes, boolean completed,
                        String source, Instant createdAt, Double startHour, String topics) {
        this(
                id,
                goalId,
                goalSubject,
                title,
                scheduledDate,
                estimatedMinutes,
                completed,
                source,
                createdAt,
                goalSubject,
                scheduledDate != null ? scheduledDate.toString() : null,
                Math.round(((double) estimatedMinutes / 60.0) * 10.0) / 10.0,
                startHour != null ? startHour : 10.0,
                "app",
                topics
        );
    }
}
