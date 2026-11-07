package com.studyplanner.goals.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGoalRequest(
        @NotBlank(message = "Môn học không được để trống.")
        @Size(max = 100, message = "Tên môn học tối đa 100 ký tự.")
        String subject,

        @NotBlank(message = "Chủ đề không được để trống.")
        String topics,

        @NotNull(message = "Ngày bắt đầu là bắt buộc.")
        LocalDate startDate,

        @NotNull(message = "Ngày thi là bắt buộc.")
        LocalDate examDate,

        @NotNull(message = "Số phút mỗi ngày là bắt buộc.")
        @Min(value = 15, message = "Số phút mỗi ngày tối thiểu là 15.")
        @Max(value = 480, message = "Số phút mỗi ngày tối đa là 480.")
        Integer minutesPerDay,

        String timezone
) {}
