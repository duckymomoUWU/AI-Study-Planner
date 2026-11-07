package com.studyplanner.goals.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.studyplanner.tasks.dto.PaginationMeta;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoalListResponse(
        List<GoalResponse> data,
        List<GoalResponse> items,
        PaginationMeta meta
) {
    public static GoalListResponse of(List<GoalResponse> goals, PaginationMeta meta) {
        return new GoalListResponse(goals, goals, meta);
    }
}
