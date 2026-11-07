package com.studyplanner.tasks.dto;

import jakarta.validation.constraints.NotNull;

public record CompleteTaskRequest(
        @NotNull(message = "Trạng thái completed là bắt buộc.")
        Boolean completed
) {}
