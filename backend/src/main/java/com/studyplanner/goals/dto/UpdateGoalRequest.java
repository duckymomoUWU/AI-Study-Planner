package com.studyplanner.goals.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateGoalRequest(
        @Size(max = 100, message = "Tên môn học tối đa 100 ký tự.")
        String subject,

        String topics,

        LocalDate startDate,

        LocalDate examDate,

        @Min(value = 15, message = "Số phút mỗi ngày tối thiểu là 15.")
        @Max(value = 480, message = "Số phút mỗi ngày tối đa là 480.")
        Integer minutesPerDay,

        Integer version,

        Integer expectedVersion
) {
    public int resolveExpectedVersion(int fallback) {
        if (expectedVersion != null) return expectedVersion;
        if (version != null) return version;
        return fallback;
    }
}
