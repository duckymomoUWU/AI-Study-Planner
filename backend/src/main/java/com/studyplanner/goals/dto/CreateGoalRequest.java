package com.studyplanner.goals.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGoalRequest(
        @NotBlank(message = "MÃ´n há»c khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng.")
        @Size(max = 100, message = "TÃªn mÃ´n há»c tá»‘i Ä‘a 100 kÃ½ tá»±.")
        String subject,

        @NotBlank(message = "Chá»§ Ä‘á» khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng.")
        String topics,

        @NotNull(message = "NgÃ y báº¯t Ä‘áº§u lÃ  báº¯t buá»™c.")
        LocalDate startDate,

        @NotNull(message = "NgÃ y thi lÃ  báº¯t buá»™c.")
        LocalDate examDate,

        @NotNull(message = "Sá»‘ phÃºt má»—i ngÃ y lÃ  báº¯t buá»™c.")
        @Min(value = 15, message = "Sá»‘ phÃºt má»—i ngÃ y tá»‘i thiá»ƒu lÃ  15.")
        @Max(value = 480, message = "Sá»‘ phÃºt má»—i ngÃ y tá»‘i Ä‘a lÃ  480.")
        Integer minutesPerDay,

        String timezone
) {}
