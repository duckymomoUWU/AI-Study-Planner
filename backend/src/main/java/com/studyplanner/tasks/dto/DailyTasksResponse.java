package com.studyplanner.tasks.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyTasksResponse(
        String date,
        int totalEstimatedMinutes,
        long completedCount,
        long totalCount,
        List<TaskResponse> items,
        List<TaskResponse> data,
        PaginationMeta meta
) {
    public static DailyTasksResponse of(String date, List<TaskResponse> tasks, PaginationMeta meta) {
        int totalMinutes = tasks.stream().mapToInt(TaskResponse::estimatedMinutes).sum();
        long completed = tasks.stream().filter(TaskResponse::completed).count();
        return new DailyTasksResponse(
                date,
                totalMinutes,
                completed,
                tasks.size(),
                tasks,
                tasks,
                meta
        );
    }
}
