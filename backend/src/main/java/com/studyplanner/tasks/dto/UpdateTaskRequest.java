package com.studyplanner.tasks.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự.")
        String title,

        LocalDate scheduledDate,

        @Min(value = 1, message = "Thời lượng tối thiểu là 1 phút.")
        @Max(value = 480, message = "Thời lượng tối đa là 480 phút.")
        Integer estimatedMinutes,

        Boolean completed
) {
    public boolean hasAnyUpdate() {
        return title != null || scheduledDate != null || estimatedMinutes != null || completed != null;
    }
}
