package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class GoalPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private GoalPolicy.GoalInput goal(LocalDate start, LocalDate exam, Integer budget) {
        return new GoalPolicy.GoalInput("Toán", "Giải tích", start, exam, budget);
    }

    private boolean hasCode(List<GoalPolicy.Violation> errors, String code) {
        return errors.stream().anyMatch(error -> error.code().equals(code));
    }

    @Test
    void acceptsInclusiveDayAndBudgetBoundaries() {
        assertTrue(GoalPolicy.validate(goal(TODAY, TODAY.plusDays(90), 15), TODAY).isEmpty());
        assertTrue(GoalPolicy.validate(goal(TODAY, TODAY.plusDays(1), 480), TODAY).isEmpty());
        assertTrue(GoalPolicy.validate(goal(TODAY.minusDays(1), TODAY, 60), TODAY).isEmpty());
    }

    @Test
    void rejectsBlankAndMissingInputs() {
        var errors = GoalPolicy.validate(
                new GoalPolicy.GoalInput("  ", "\t\n", null, null, null), TODAY);
        assertTrue(hasCode(errors, "required"));
        assertTrue(hasCode(errors, "out_of_range"));
        assertTrue(errors.stream().anyMatch(error -> error.field().equals("subject")));
        assertTrue(errors.stream().anyMatch(error -> error.field().equals("topics")));
    }

    @Test
    void rejectsPastExamAndInvalidDateOrder() {
        assertTrue(hasCode(GoalPolicy.validate(goal(TODAY.minusDays(2), TODAY.minusDays(1), 30), TODAY),
                "past_date"));
        assertTrue(hasCode(GoalPolicy.validate(goal(TODAY, TODAY, 30), TODAY),
                "not_after_start"));
        assertTrue(hasCode(GoalPolicy.validate(goal(TODAY.plusDays(2), TODAY.plusDays(1), 30), TODAY),
                "not_after_start"));
        assertTrue(hasCode(GoalPolicy.validate(goal(TODAY, TODAY.plusDays(91), 30), TODAY),
                "over_90_days"));
    }

    @Test
    void rejectsBudgetOutsideInclusiveBounds() {
        assertTrue(hasCode(GoalPolicy.validate(goal(TODAY, TODAY.plusDays(7), 14), TODAY),
                "out_of_range"));
        assertTrue(hasCode(GoalPolicy.validate(goal(TODAY, TODAY.plusDays(7), 481), TODAY),
                "out_of_range"));
    }

    @Test
    void updateRejectsTasksOnExamDayOrLater() {
        var tasks = List.of(new GoalPolicy.ScheduledTask(TODAY.plusDays(7), 20),
                            new GoalPolicy.ScheduledTask(TODAY.plusDays(8), 20));
        var errors = GoalPolicy.validateUpdate(goal(TODAY, TODAY.plusDays(7), 60), tasks, TODAY);
        assertTrue(hasCode(errors, "task_after_deadline"));
    }

    @Test
    void updateRejectsCombinedDailyTaskDurationBeyondNewBudget() {
        var tasks = List.of(new GoalPolicy.ScheduledTask(TODAY.plusDays(1), 60),
                            new GoalPolicy.ScheduledTask(TODAY.plusDays(1), 50));
        var errors = GoalPolicy.validateUpdate(goal(TODAY, TODAY.plusDays(10), 100), tasks, TODAY);
        assertTrue(hasCode(errors, "daily_budget_exceeded"));
    }

    @Test
    void updateAcceptsTasksWithinDeadlineAndBudget() {
        var tasks = List.of(new GoalPolicy.ScheduledTask(TODAY.plusDays(1), 60),
                            new GoalPolicy.ScheduledTask(TODAY.plusDays(1), 40),
                            new GoalPolicy.ScheduledTask(TODAY.plusDays(2), 100));
        var errors = GoalPolicy.validateUpdate(goal(TODAY, TODAY.plusDays(10), 100), tasks, TODAY);
        assertTrue(errors.isEmpty());
    }

    @Test
    void updateFailsClosedOnCorruptExistingTask() {
        var tasks = List.of(new GoalPolicy.ScheduledTask(TODAY.plusDays(1), 0));
        var errors = GoalPolicy.validateUpdate(goal(TODAY, TODAY.plusDays(10), 100), tasks, TODAY);
        assertFalse(errors.isEmpty());
        assertTrue(hasCode(errors, "invalid_existing_task"));
    }
}
