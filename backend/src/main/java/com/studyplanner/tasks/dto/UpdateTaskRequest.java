package com.studyplanner.tasks.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(
        @Size(max = 200, message = "TiÃªu Ä‘á» tá»‘i Ä‘a 200 kÃ½ tá»±.")
        String title,

        LocalDate scheduledDate,

        @Min(value = 1, message = "Thá»i lÆ°á»£ng tá»‘i thiá»ƒu lÃ  1 phÃºt.")
        @Max(value = 480, message = "Thá»i lÆ°á»£ng tá»‘i Ä‘a lÃ  480 phÃºt.")
        Integer estimatedMinutes,

        Boolean completed
) {
    public boolean hasAnyUpdate() {
        return title != null || scheduledDate != null || estimatedMinutes != null || completed != null;
    }
}
