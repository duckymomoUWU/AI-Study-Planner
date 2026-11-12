package com.studyplanner.tasks;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class TaskPolicyTest {
    private static final LocalDate START = LocalDate.of(2026, 11, 9);
    private static final LocalDate EXAM = LocalDate.of(2026, 11, 12);

    @Test
    void acceptsValidTaskInput() {
        var input = new TaskPolicy.TaskInput("Luyện SQL JOIN", LocalDate.of(2026, 11, 10), 60);
        var violations = TaskPolicy.validateInput(input);
        assertTrue(violations.isEmpty());
    }

    @Test
    void rejectsBlankAndOverlongTitle() {
        var blank = new TaskPolicy.TaskInput("   ", LocalDate.of(2026, 11, 10), 60);
        var errors = TaskPolicy.validateInput(blank);
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("title") && e.issue().equals("required")));

        String longTitle = "A".repeat(201);
        var tooLong = new TaskPolicy.TaskInput(longTitle, LocalDate.of(2026, 11, 10), 60);
        errors = TaskPolicy.validateInput(tooLong);
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("title") && e.issue().equals("too_long")));
    }

    @Test
    void rejectsEstimatedMinutesOutsideOneToFourHundredEighty() {
        var zero = new TaskPolicy.TaskInput("Title", LocalDate.of(2026, 11, 10), 0);
        var errors = TaskPolicy.validateInput(zero);
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("estimatedMinutes") && e.issue().equals("out_of_range")));

        var negative = new TaskPolicy.TaskInput("Title", LocalDate.of(2026, 11, 10), -10);
        errors = TaskPolicy.validateInput(negative);
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("estimatedMinutes") && e.issue().equals("out_of_range")));

        var over = new TaskPolicy.TaskInput("Title", LocalDate.of(2026, 11, 10), 481);
        errors = TaskPolicy.validateInput(over);
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("estimatedMinutes") && e.issue().equals("out_of_range")));

        var bound1 = new TaskPolicy.TaskInput("Title", LocalDate.of(2026, 11, 10), 1);
        assertTrue(TaskPolicy.validateInput(bound1).isEmpty());

        var bound480 = new TaskPolicy.TaskInput("Title", LocalDate.of(2026, 11, 10), 480);
        assertTrue(TaskPolicy.validateInput(bound480).isEmpty());
    }

    @Test
    void validateWithinGoalAcceptsStartDateAndDayBeforeExamDate() {
        // Start date is valid
        assertTrue(TaskPolicy.validateWithinGoal(START, START, EXAM).isEmpty());
        // Day before exam date is valid (2026-11-11)
        assertTrue(TaskPolicy.validateWithinGoal(EXAM.minusDays(1), START, EXAM).isEmpty());
    }

    @Test
    void validateWithinGoalRejectsBeforeStartDateAndOnOrAfterExamDate() {
        // Before start date (2026-11-08)
        var beforeStart = TaskPolicy.validateWithinGoal(START.minusDays(1), START, EXAM);
        assertFalse(beforeStart.isEmpty());
        assertTrue(beforeStart.stream().anyMatch(e -> e.issue().equals("before_start_date")));

        // On exam date (2026-11-12)
        var onExam = TaskPolicy.validateWithinGoal(EXAM, START, EXAM);
        assertFalse(onExam.isEmpty());
        assertTrue(onExam.stream().anyMatch(e -> e.issue().equals("on_or_after_exam_date")));

        // After exam date (2026-11-13)
        var afterExam = TaskPolicy.validateWithinGoal(EXAM.plusDays(1), START, EXAM);
        assertFalse(afterExam.isEmpty());
        assertTrue(afterExam.stream().anyMatch(e -> e.issue().equals("on_or_after_exam_date")));
    }

    @Test
    void checkDailyBudgetEnforcesDailyMinuteLimit() {
        LocalDate date = LocalDate.of(2026, 11, 10);
        // 30 already scheduled + 30 new <= 60 budget -> valid
        assertDoesNotThrow(() -> TaskPolicy.checkDailyBudget(date, 30, 30, 60));

        // 30 already scheduled + 31 new > 60 budget -> throws DailyBudgetExceededException
        var ex = assertThrows(DailyBudgetExceededException.class,
                () -> TaskPolicy.checkDailyBudget(date, 30, 31, 60));
        assertEquals(date, ex.getScheduledDate());
        assertEquals(60, ex.getBudgetMinutes());
        assertEquals(61, ex.getScheduledMinutes());
        assertEquals(1, ex.getExcessMinutes());
    }
}
