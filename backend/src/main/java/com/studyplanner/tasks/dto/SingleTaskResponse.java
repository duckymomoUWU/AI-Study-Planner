package com.studyplanner.tasks.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SingleTaskResponse(
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
        String topics,
        TaskResponse data
) {
    public static SingleTaskResponse of(TaskResponse task) {
        return new SingleTaskResponse(
                task.id(),
                task.goalId(),
                task.goalSubject(),
                task.title(),
                task.scheduledDate(),
                task.estimatedMinutes(),
                task.completed(),
                task.source(),
                task.createdAt(),
                task.subject(),
                task.date(),
                task.durationHours(),
                task.startHour(),
                task.type(),
                task.topics(),
                task
        );
    }
}
