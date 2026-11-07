package com.studyplanner.goals;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure domain rules for Jira ASP-9. No authentication or persistence is implied here. */
public final class GoalPolicy {
    private GoalPolicy() {}

    public record GoalInput(String subject, String topics, LocalDate startDate,
                            LocalDate examDate, Integer minutesPerDay) {}

    public record ScheduledTask(LocalDate scheduledDate, int estimatedMinutes) {}

    public record Violation(String field, String code, String message) {}

    /** The caller must supply the current date in the goal's effective timezone. */
    public static List<Violation> validate(GoalInput goal, LocalDate today) {
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(today, "today");
        var errors = new ArrayList<Violation>();

        if (goal.subject() == null || goal.subject().isBlank()) {
            errors.add(new Violation("subject", "required", "Môn học không được để trống."));
        }
        if (goal.topics() == null || goal.topics().isBlank()) {
            errors.add(new Violation("topics", "required", "Chủ đề không được để trống."));
        }
        if (goal.startDate() == null) {
            errors.add(new Violation("startDate", "required", "Ngày bắt đầu là bắt buộc."));
        }
        if (goal.examDate() == null) {
            errors.add(new Violation("examDate", "required", "Ngày thi là bắt buộc."));
        } else if (goal.examDate().isBefore(today)) {
            errors.add(new Violation("examDate", "past_date", "Ngày thi không được ở trong quá khứ."));
        }
        if (goal.startDate() != null && goal.examDate() != null) {
            if (!goal.examDate().isAfter(goal.startDate())) {
                errors.add(new Violation("examDate", "not_after_start", "Ngày thi phải sau ngày bắt đầu."));
            } else if (ChronoUnit.DAYS.between(goal.startDate(), goal.examDate()) > 90) {
                errors.add(new Violation("examDate", "over_90_days", "Khoảng thời gian không được vượt quá 90 ngày."));
            }
        }
        if (goal.minutesPerDay() == null || goal.minutesPerDay() < 15 || goal.minutesPerDay() > 480) {
            errors.add(new Violation("minutesPerDay", "out_of_range", "Số phút mỗi ngày phải là số nguyên từ 15 đến 480."));
        }
        return List.copyOf(errors);
    }

    /** Verify that changing a goal does not invalidate already scheduled tasks. */
    public static List<Violation> validateUpdate(GoalInput goal, List<ScheduledTask> existingTasks,
                                                 LocalDate today) {
        Objects.requireNonNull(existingTasks, "existingTasks");
        var errors = new ArrayList<>(validate(goal, today));
        Map<LocalDate, Long> minutesByDate = new HashMap<>();
        for (ScheduledTask task : existingTasks) {
            Objects.requireNonNull(task, "task");
            if (task.scheduledDate() == null || task.estimatedMinutes() <= 0) {
                errors.add(new Violation("tasks", "invalid_existing_task", "Công việc hiện có không hợp lệ."));
                continue;
            }
            if (goal.examDate() != null && !task.scheduledDate().isBefore(goal.examDate())) {
                errors.add(new Violation("examDate", "task_after_deadline",
                        "Có công việc được xếp vào ngày thi hoặc sau ngày thi."));
            }
            minutesByDate.merge(task.scheduledDate(), (long) task.estimatedMinutes(), Long::sum);
        }
        if (goal.minutesPerDay() != null && goal.minutesPerDay() >= 15 && goal.minutesPerDay() <= 480) {
            for (var day : minutesByDate.entrySet()) {
                if (day.getValue() > goal.minutesPerDay()) {
                    errors.add(new Violation("minutesPerDay", "daily_budget_exceeded",
                            "Công việc ngày " + day.getKey() + " vượt ngân sách học mỗi ngày."));
                }
            }
        }
        return List.copyOf(errors);
    }
}
