package com.studyplanner.tasks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TaskPolicy {
    private TaskPolicy() {}

    public record TaskInput(String title, LocalDate scheduledDate, Integer estimatedMinutes) {}

    public static List<TaskValidationException.FieldViolation> validateInput(TaskInput input) {
        Objects.requireNonNull(input, "input must not be null");
        List<TaskValidationException.FieldViolation> violations = new ArrayList<>();

        if (input.title() == null || input.title().trim().isEmpty()) {
            violations.add(new TaskValidationException.FieldViolation("title", "required", "Tiêu đề công việc không được để trống."));
        } else if (input.title().trim().length() > 200) {
            violations.add(new TaskValidationException.FieldViolation("title", "too_long", "Tiêu đề không được vượt quá 200 ký tự."));
        }

        if (input.scheduledDate() == null) {
            violations.add(new TaskValidationException.FieldViolation("scheduledDate", "required", "Ngày học là bắt buộc."));
        }

        if (input.estimatedMinutes() == null) {
            violations.add(new TaskValidationException.FieldViolation("estimatedMinutes", "required", "Thời lượng là bắt buộc."));
        } else if (input.estimatedMinutes() < 1 || input.estimatedMinutes() > 480) {
            violations.add(new TaskValidationException.FieldViolation("estimatedMinutes", "out_of_range", "Thời lượng phải là số nguyên từ 1 đến 480 phút."));
        }

        return List.copyOf(violations);
    }

    public static List<TaskValidationException.FieldViolation> validateWithinGoal(
            LocalDate scheduledDate,
            LocalDate startDate,
            LocalDate examDate) {
        List<TaskValidationException.FieldViolation> violations = new ArrayList<>();
        if (scheduledDate == null || startDate == null || examDate == null) {
            return violations;
        }

        if (scheduledDate.isBefore(startDate)) {
            violations.add(new TaskValidationException.FieldViolation(
                    "scheduledDate",
                    "before_start_date",
                    "Ngày học không được trước ngày bắt đầu mục tiêu (" + startDate + ")."
            ));
        }

        if (!scheduledDate.isBefore(examDate)) {
            violations.add(new TaskValidationException.FieldViolation(
                    "scheduledDate",
                    "on_or_after_exam_date",
                    "Ngày học phải trước ngày thi (" + examDate + "); ngày thi không được xếp lịch học."
            ));
        }

        return List.copyOf(violations);
    }

    public static void checkDailyBudget(
            LocalDate scheduledDate,
            int currentUsedMinutes,
            int newTaskMinutes,
            int minutesPerDay) {
        int totalScheduled = currentUsedMinutes + newTaskMinutes;
        if (totalScheduled > minutesPerDay) {
            int excess = totalScheduled - minutesPerDay;
            throw new DailyBudgetExceededException(scheduledDate, minutesPerDay, totalScheduled, excess);
        }
    }
}
