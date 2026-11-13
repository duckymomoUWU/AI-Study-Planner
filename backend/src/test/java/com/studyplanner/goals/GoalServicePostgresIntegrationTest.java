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
        var goal = create(ownerA, input("Toán", 90, today.plusDays(30)));

        assertEquals(1, list(ownerA).size());
        assertEquals(goal.id(), service.getGoal(ownerA, goal.id()).id());
        assertTrue(list(ownerB).isEmpty());
        assertThrows(GoalService.GoalNotFoundException.class,
                () -> service.getGoal(ownerB, goal.id()));
    }

    @Test
    void lowerPreferenceDoesNotInvalidateExistingTasks() {
        var goal = create(ownerA, input("Toán", 120, today.plusDays(30)));
        LocalDate taskDay = today.plusDays(1);
        insertTask(goal.id(), taskDay, 60);
        insertTask(goal.id(), taskDay, 50);

        var updated = update(ownerA, goal.id(), input("New subject", 100, goal.examDate()), goal.version());
        assertEquals(100, updated.minutesPerDay());
        assertEquals(1, updated.version());
        assertEquals(2, count("study_tasks", goal.id()));
    }

    @Test
    void updateRejectsExistingTaskOnOrAfterNewExamDate() {
        var goal = create(ownerA, input("Lý", 90, today.plusDays(30)));
        insertTask(goal.id(), today.plusDays(20), 30);

        var exception = assertThrows(GoalService.GoalValidationException.class,
                () -> update(ownerA, goal.id(),
                        input("Lý", 90, today.plusDays(15)), goal.version()));

        assertTrue(exception.violations().stream()
                .anyMatch(violation -> violation.code().equals("task_on_or_after_exam_date")));
        assertEquals(today.plusDays(30), service.getGoal(ownerA, goal.id()).examDate());
    }

    @Test
    void successfulUpdateIncrementsVersionAndRejectsStaleVersion() {
        var goal = create(ownerA, input("Hóa", 90, today.plusDays(30)));

        var updated = update(ownerA, goal.id(),
                input("Hóa hữu cơ", 120, goal.examDate()), goal.version());

        assertEquals("Hóa hữu cơ", updated.subject());
        assertEquals(1, updated.version());
        assertThrows(GoalService.GoalConflictException.class,
                () -> update(ownerA, goal.id(),
                        input("Hóa cũ", 120, goal.examDate()), 0));
        assertEquals("Hóa hữu cơ", service.getGoal(ownerA, goal.id()).subject());
    }

    @Test
    void deleteCascadesTasksAndPlanDraftsButOnlyForTheOwnedGoal() {
        var goal = create(ownerA, input("Sinh", 60, today.plusDays(20)));
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

    @Test
    void updateByAnotherAccountIsRejectedAndLeavesGoalUntouched() {
        var goal = create(ownerA, input("Toán", 90, today.plusDays(30)));

        assertThrows(GoalService.GoalNotFoundException.class,
                () -> update(ownerB, goal.id(), input("Hack", 200, today.plusDays(40)), goal.version()));

        var unchanged = service.getGoal(ownerA, goal.id());
        assertEquals("Toán", unchanged.subject());
        assertEquals(90, unchanged.minutesPerDay());
        assertEquals(0, unchanged.version());
    }

    @Test
    void updateBoundariesWithRealTasks() {
        var goal = create(ownerA, input("Toán", 120, today.plusDays(30)));
        var day = today.plusDays(3);
        insertTask(goal.id(), day, 60);
        insertTask(goal.id(), day, 40);
        insertTask(goal.id(), today.plusDays(9), 30);

        // Daily total 100 equals the new budget; last task is the day before the new exam date.
        var ok = update(ownerA, goal.id(), input("Toán", 100, today.plusDays(10)), goal.version());
        assertEquals(100, ok.minutesPerDay());
        assertEquals(today.plusDays(10), ok.examDate());
        assertEquals(1, ok.version());

        var lower = update(ownerA, goal.id(), input("Math", 99, today.plusDays(10)), 1);
        assertEquals(2, lower.version());

        // Exam date equal to the last task's day is rejected.
        var onExamDay = assertThrows(GoalService.GoalValidationException.class,
                () -> update(ownerA, goal.id(), input("Toán", 100, today.plusDays(9)), 2));
        assertTrue(onExamDay.violations().stream().anyMatch(v -> v.code().equals("task_on_or_after_exam_date")));

        var stored = service.getGoal(ownerA, goal.id());
        assertEquals(99, stored.minutesPerDay());
        assertEquals(today.plusDays(10), stored.examDate());
        assertEquals(2, stored.version());
        assertEquals(3, count("study_tasks", goal.id()));
    }

    @Test
    void updateOnlyChecksTasksOfTheSameGoal() {
        var goalA = create(ownerA, input("Toán", 120, today.plusDays(30)));
        var goalB = create(ownerA, input("Lý", 120, today.plusDays(30)));
        insertTask(goalB.id(), today.plusDays(5), 110);

        // Goal B's heavy day must not block lowering goal A's budget (budget is per goal).
        var updated = update(ownerA, goalA.id(), input("Toán", 30, today.plusDays(30)), goalA.version());
        assertEquals(30, updated.minutesPerDay());
    }

    private GoalJdbcRepository.StoredGoal create(UUID owner, GoalPolicy.GoalInput input) {
        return service.createGoal(owner, input, null);
    }

    /** Sends every editable field, like a full edit form would. */
    private GoalJdbcRepository.StoredGoal update(UUID owner, UUID goalId, GoalPolicy.GoalInput input, int version) {
        return service.patchGoal(owner, goalId, new GoalPatch(input.subject(), input.topics(), input.startDate(),
                input.examDate(), input.minutesPerDay(), null), version);
    }

    private java.util.List<GoalJdbcRepository.StoredGoal> list(UUID owner) {
        return service.listGoals(owner, 1, 100).items();
    }

    @Test
    void listIsPagedSortedNewestFirstAndCountsOnlyOwnGoals() {
        var first = create(ownerA, input("Một", 60, today.plusDays(10)));
        var second = create(ownerA, input("Hai", 60, today.plusDays(10)));
        var third = create(ownerA, input("Ba", 60, today.plusDays(10)));
        create(ownerB, input("Của B", 60, today.plusDays(10)));

        var page1 = service.listGoals(ownerA, 1, 2);
        var page2 = service.listGoals(ownerA, 2, 2);

        assertEquals(3, page1.totalItems());
        assertEquals(java.util.List.of(third.id(), second.id()), page1.items().stream().map(g -> g.id()).toList());
        assertEquals(java.util.List.of(first.id()), page2.items().stream().map(g -> g.id()).toList());
        assertTrue(service.listGoals(ownerA, 3, 2).items().isEmpty());
    }

    @Test
    void patchChangesOnlySentFieldsAndTimezoneAndReturnsCreatedAt() {
        var goal = create(ownerA, input("Toán", 90, today.plusDays(30)));
        assertTrue(goal.createdAt() != null);

        var patched = service.patchGoal(ownerA, goal.id(),
                new GoalPatch(null, null, null, null, 45, "UTC"), goal.version());

        assertEquals("Toán", patched.subject());
        assertEquals(45, patched.minutesPerDay());
        assertEquals("UTC", patched.timezone());
        assertEquals(goal.examDate(), patched.examDate());
        assertEquals(goal.createdAt(), patched.createdAt());
        assertEquals(1, patched.version());
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
