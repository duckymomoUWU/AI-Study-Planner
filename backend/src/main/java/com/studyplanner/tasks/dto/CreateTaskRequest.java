package com.studyplanner.tasks.dto;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự.")
        String title,

        LocalDate scheduledDate,

        LocalDate date,

        Integer estimatedMinutes,

        Double durationHours,

        Double startHour,

        String subject,

        UUID goalId,

        String type
) {
    public CreateTaskRequest(String title, LocalDate scheduledDate, Integer estimatedMinutes) {
        this(title, scheduledDate, scheduledDate, estimatedMinutes, null, 10.0, null, null, "app");
    }

    public LocalDate resolveDate() {
        if (scheduledDate != null) return scheduledDate;
        if (date != null) return date;
        return null;
    }

    public Integer resolveMinutes() {
        if (estimatedMinutes != null) return estimatedMinutes;
        if (durationHours != null && durationHours > 0) {
            return (int) Math.round(durationHours * 60);
        }
        return 60; // default 60 mins if unspecified
    }

    public Double resolveStartHour() {
        return startHour != null ? startHour : 10.0;
    }
}
