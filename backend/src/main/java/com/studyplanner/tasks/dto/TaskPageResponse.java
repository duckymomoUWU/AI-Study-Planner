package com.studyplanner.tasks.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskPageResponse(
        List<TaskResponse> data,
        List<TaskResponse> items,
        PaginationMeta meta
) {
    public static TaskPageResponse of(List<TaskResponse> tasks, PaginationMeta meta) {
        return new TaskPageResponse(tasks, tasks, meta);
    }
}
