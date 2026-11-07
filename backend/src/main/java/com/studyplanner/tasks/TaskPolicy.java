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
            violations.add(new TaskValidationException.FieldViolation("title", "required", "TiÃªu Ä‘á» cÃ´ng viá»‡c khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng."));
        } else if (input.title().trim().length() > 200) {
            violations.add(new TaskValidationException.FieldViolation("title", "too_long", "TiÃªu Ä‘á» khÃ´ng Ä‘Æ°á»£c vÆ°á»£t quÃ¡ 200 kÃ½ tá»±."));
        }

        if (input.scheduledDate() == null) {
            violations.add(new TaskValidationException.FieldViolation("scheduledDate", "required", "NgÃ y há»c lÃ  báº¯t buá»™c."));
        }

        if (input.estimatedMinutes() == null) {
            violations.add(new TaskValidationException.FieldViolation("estimatedMinutes", "required", "Thá»i lÆ°á»£ng lÃ  báº¯t buá»™c."));
        } else if (input.estimatedMinutes() < 1 || input.estimatedMinutes() > 480) {
            violations.add(new TaskValidationException.FieldViolation("estimatedMinutes", "out_of_range", "Thá»i lÆ°á»£ng pháº£i lÃ  sá»‘ nguyÃªn tá»« 1 Ä‘áº¿n 480 phÃºt."));
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
                    "NgÃ y há»c khÃ´ng Ä‘Æ°á»£c trÆ°á»›c ngÃ y báº¯t Ä‘áº§u má»¥c tiÃªu (" + startDate + ")."
            ));
        }

        if (!scheduledDate.isBefore(examDate)) {
            violations.add(new TaskValidationException.FieldViolation(
                    "scheduledDate",
                    "on_or_after_exam_date",
                    "NgÃ y há»c pháº£i trÆ°á»›c ngÃ y thi (" + examDate + "); ngÃ y thi khÃ´ng Ä‘Æ°á»£c xáº¿p lá»‹ch há»c."
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
