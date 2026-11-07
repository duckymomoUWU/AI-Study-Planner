package com.studyplanner.tasks.dto;

import jakarta.validation.constraints.NotNull;

public record CompleteTaskRequest(
        @NotNull(message = "Tráº¡ng thÃ¡i completed lÃ  báº¯t buá»™c.")
        Boolean completed
) {}
