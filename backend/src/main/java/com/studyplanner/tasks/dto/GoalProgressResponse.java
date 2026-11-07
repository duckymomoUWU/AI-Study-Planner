package com.studyplanner.tasks.dto;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoalProgressResponse(
        UUID goalId,
        long totalTasks,
        long completedTasks,
        long remainingTasks,
        double completionRate,
        GoalProgressData data
) {
    public record GoalProgressData(
            UUID goalId,
            long totalTasks,
            long completedTasks,
            long remainingTasks,
            double completionRate
    ) {}

    public static GoalProgressResponse of(UUID goalId, long totalTasks, long completedTasks) {
        long remaining = Math.max(0, totalTasks - completedTasks);
        double rate = totalTasks == 0 ? 0.0 : (double) completedTasks / totalTasks;
        GoalProgressData data = new GoalProgressData(goalId, totalTasks, completedTasks, remaining, rate);
        return new GoalProgressResponse(goalId, totalTasks, completedTasks, remaining, rate, data);
    }
}
