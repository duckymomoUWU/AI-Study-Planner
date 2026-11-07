package com.studyplanner.goals.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateGoalRequest(
        @Size(max = 100, message = "TÃªn mÃ´n há»c tá»‘i Ä‘a 100 kÃ½ tá»±.")
        String subject,

        String topics,

        LocalDate startDate,

        LocalDate examDate,

        @Min(value = 15, message = "Sá»‘ phÃºt má»—i ngÃ y tá»‘i thiá»ƒu lÃ  15.")
        @Max(value = 480, message = "Sá»‘ phÃºt má»—i ngÃ y tá»‘i Ä‘a lÃ  480.")
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
