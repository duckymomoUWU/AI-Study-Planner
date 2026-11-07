package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs against the PostgreSQL service configured in GitHub Actions.
 * Enable locally with -Dgoal.integration=true and a disposable PostgreSQL database.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "goal.integration", matches = "true")
class GoalServicePostgresIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GoalService service;

    private UUID ownerA;
    private UUID ownerB;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        // This test profile is only enabled against a disposable database.
        jdbc.update("DELETE FROM app_users");
        ownerA = createUser();
        ownerB = createUser();
        today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    void createListAndGetAreRestrictedToTheCurrentOwner() {
        var goal = service.createGoal(ownerA, input("Toán", 90, today.plusDays(30)));

        assertEquals(1, service.listGoals(ownerA).size());
        assertEquals(goal.id(), service.getGoal(ownerA, goal.id()).id());
        assertTrue(service.listGoals(ownerB).isEmpty());
        assertThrows(GoalService.GoalNotFoundException.class,
                () -> service.getGoal(ownerB, goal.id()));
    }

    @Test
    void updateRejectsDailyOverBudgetWithoutPartiallyChangingGoal() {
        var goal = service.createGoal(ownerA, input("Toán", 120, today.plusDays(30)));
        LocalDate taskDay = today.plusDays(1);
        insertTask(goal.id(), taskDay, 60);
        insertTask(goal.id(), taskDay, 50);

        var exception = assertThrows(GoalService.GoalValidationException.class,
                () -> service.updateGoal(ownerA, goal.id(),
                        input("Toán nâng cao", 100, goal.examDate()), goal.version()));

        assertTrue(exception.violations().stream()
                .anyMatch(violation -> violation.code().equals("daily_budget_exceeded")));
        var unchanged = service.getGoal(ownerA, goal.id());
        assertEquals("Toán", unchanged.subject());
        assertEquals(120, unchanged.minutesPerDay());
        assertEquals(0, unchanged.version());
    }

    @Test
    void updateRejectsExistingTaskOnOrAfterNewExamDate() {
        var goal = service.createGoal(ownerA, input("Lý", 90, today.plusDays(30)));
        insertTask(goal.id(), today.plusDays(20), 30);

        var exception = assertThrows(GoalService.GoalValidationException.class,
                () -> service.updateGoal(ownerA, goal.id(),
                        input("Lý", 90, today.plusDays(15)), goal.version()));

        assertTrue(exception.violations().stream()
                .anyMatch(violation -> violation.code().equals("task_after_deadline")));
        assertEquals(today.plusDays(30), service.getGoal(ownerA, goal.id()).examDate());
    }

    @Test
    void successfulUpdateIncrementsVersionAndRejectsStaleVersion() {
        var goal = service.createGoal(ownerA, input("Hóa", 90, today.plusDays(30)));

        var updated = service.updateGoal(ownerA, goal.id(),
                input("Hóa hữu cơ", 120, goal.examDate()), goal.version());

        assertEquals("Hóa hữu cơ", updated.subject());
        assertEquals(1, updated.version());
        assertThrows(GoalService.GoalConflictException.class,
                () -> service.updateGoal(ownerA, goal.id(),
                        input("Hóa cũ", 120, goal.examDate()), 0));
        assertEquals("Hóa hữu cơ", service.getGoal(ownerA, goal.id()).subject());
    }

    @Test
    void deleteCascadesTasksAndPlanDraftsButOnlyForTheOwnedGoal() {
        var goal = service.createGoal(ownerA, input("Sinh", 60, today.plusDays(20)));
        insertTask(goal.id(), today.plusDays(1), 30);
        jdbc.update(
                "INSERT INTO plan_drafts (goal_id, goal_version, proposal, expires_at) "
                        + "VALUES (?, ?, ?::jsonb, now() + interval '1 day')",
                goal.id(), goal.version(), "{\"tasks\":[]}"
        );

        assertThrows(GoalService.GoalNotFoundException.class,
                () -> service.deleteGoal(ownerB, goal.id()));
        assertEquals(1, count("study_tasks", goal.id()));
        assertEquals(1, count("plan_drafts", goal.id()));

        service.deleteGoal(ownerA, goal.id());

        assertEquals(0, count("study_goals", goal.id()));
        assertEquals(0, count("study_tasks", goal.id()));
        assertEquals(0, count("plan_drafts", goal.id()));
    }

    private GoalPolicy.GoalInput input(String subject, int budget, LocalDate examDate) {
        return new GoalPolicy.GoalInput(subject, "Chương 1", today, examDate, budget);
    }

    private UUID createUser() {
        return jdbc.queryForObject(
                "INSERT INTO app_users (email, password_hash) VALUES (?, ?) RETURNING id",
                UUID.class, "integration-" + UUID.randomUUID() + "@example.test", "not-a-real-password-hash"
        );
    }

    private void insertTask(UUID goalId, LocalDate date, int minutes) {
        jdbc.update(
                "INSERT INTO study_tasks (goal_id, title, scheduled_date, estimated_minutes) VALUES (?, ?, ?, ?)",
                goalId, "Integration test task", date, minutes
        );
    }

    private int count(String table, UUID goalId) {
        if (!table.equals("study_goals") && !table.equals("study_tasks") && !table.equals("plan_drafts")) {
            throw new IllegalArgumentException("Unexpected table");
        }
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE "
                + (table.equals("study_goals") ? "id" : "goal_id") + " = ?", Integer.class, goalId);
    }
}
