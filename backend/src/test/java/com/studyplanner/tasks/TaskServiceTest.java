package com.studyplanner.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.studyplanner.goals.GoalJdbcRepository;
import com.studyplanner.tasks.dto.CreateTaskRequest;
import com.studyplanner.tasks.dto.DailyTasksResponse;
import com.studyplanner.tasks.dto.GoalProgressResponse;
import com.studyplanner.tasks.dto.SingleTaskResponse;
import com.studyplanner.tasks.dto.TaskPageResponse;
import com.studyplanner.tasks.dto.UpdateTaskRequest;

class TaskServiceTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GOAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TASK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final LocalDate START = LocalDate.of(2026, 11, 9);
    private static final LocalDate EXAM = LocalDate.of(2026, 11, 12);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneOffset.UTC);

    private TaskJdbcRepository taskRepository;
    private GoalJdbcRepository goalRepository;
    private TaskService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskJdbcRepository.class);
        goalRepository = mock(GoalJdbcRepository.class);
        service = new TaskService(taskRepository, goalRepository, CLOCK, "Asia/Ho_Chi_Minh");
    }

    private GoalJdbcRepository.StoredGoal mockGoal(int version, int minutesPerDay) {
        return new GoalJdbcRepository.StoredGoal(
                GOAL_ID, USER_ID, "CÆ¡ sá»Ÿ dá»¯ liá»‡u", "SQL, BCNF",
                START, EXAM, minutesPerDay, "Asia/Ho_Chi_Minh", version
        );
    }

    private TaskJdbcRepository.StoredTask mockTask(int minutes, boolean completed) {
        return new TaskJdbcRepository.StoredTask(
                TASK_ID, GOAL_ID, "CÆ¡ sá»Ÿ dá»¯ liá»‡u", "Ã”n SQL JOIN",
                LocalDate.of(2026, 11, 10), minutes, completed, "manual", Instant.now()
        );
    }

    @Test
    void createTaskSucceedsAndIncrementsGoalVersion() {
        var goal = mockGoal(0, 60);
        when(goalRepository.lockOwned(USER_ID, GOAL_ID)).thenReturn(Optional.of(goal));
        when(taskRepository.getDailyUsedMinutes(GOAL_ID, LocalDate.of(2026, 11, 10), null)).thenReturn(20);

        var stored = mockTask(30, false);
        when(taskRepository.insertTask(eq(GOAL_ID), eq("Ã”n SQL JOIN"), eq(LocalDate.of(2026, 11, 10)), eq(30), eq("manual"), eq("CÆ¡ sá»Ÿ dá»¯ liá»‡u")))
                .thenReturn(stored);

        var request = new CreateTaskRequest("  Ã”n SQL JOIN  ", LocalDate.of(2026, 11, 10), 30);
        SingleTaskResponse res = service.createTask(USER_ID, GOAL_ID, request);

        assertNotNull(res);
        assertEquals("Ã”n SQL JOIN", res.title());
        assertEquals(30, res.estimatedMinutes());
        assertFalse(res.completed());
        assertEquals("manual", res.source());

        verify(goalRepository).lockOwned(USER_ID, GOAL_ID);
        verify(taskRepository).insertTask(GOAL_ID, "Ã”n SQL JOIN", LocalDate.of(2026, 11, 10), 30, "manual", "CÆ¡ sá»Ÿ dá»¯ liá»‡u");
        verify(taskRepository).incrementGoalVersion(USER_ID, GOAL_ID);
    }

    @Test
    void createTaskFailsWhenDailyBudgetExceeded() {
        var goal = mockGoal(0, 60);
        when(goalRepository.lockOwned(USER_ID, GOAL_ID)).thenReturn(Optional.of(goal));
        when(taskRepository.getDailyUsedMinutes(GOAL_ID, LocalDate.of(2026, 11, 10), null)).thenReturn(40);

        var request = new CreateTaskRequest("BÃ i táº­p lá»›n", LocalDate.of(2026, 11, 10), 30);
        var ex = assertThrows(DailyBudgetExceededException.class, () -> service.createTask(USER_ID, GOAL_ID, request));

        assertEquals(60, ex.getBudgetMinutes());
        assertEquals(70, ex.getScheduledMinutes());
        assertEquals(10, ex.getExcessMinutes());
        verify(taskRepository, never()).insertTask(any(), any(), any(), anyInt(), any(), any());
        verify(taskRepository, never()).incrementGoalVersion(any(), any());
    }

    @Test
    void createTaskRejectsDateOutsideGoalSchedule() {
        var goal = mockGoal(0, 60);
        when(goalRepository.lockOwned(USER_ID, GOAL_ID)).thenReturn(Optional.of(goal));

        // Date on exam date (2026-11-12)
        var onExam = new CreateTaskRequest("Thi", EXAM, 30);
        assertThrows(TaskValidationException.class, () -> service.createTask(USER_ID, GOAL_ID, onExam));

        // Date before start date (2026-11-08)
        var beforeStart = new CreateTaskRequest("Sá»›m", START.minusDays(1), 30);
        assertThrows(TaskValidationException.class, () -> service.createTask(USER_ID, GOAL_ID, beforeStart));

        verify(taskRepository, never()).insertTask(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void createTaskFailsIfGoalNotFoundOrNotOwned() {
        when(goalRepository.lockOwned(USER_ID, GOAL_ID)).thenReturn(Optional.empty());

        var request = new CreateTaskRequest("Há»c bÃ i", LocalDate.of(2026, 11, 10), 30);
        assertThrows(TaskNotFoundException.class, () -> service.createTask(USER_ID, GOAL_ID, request));
    }

    @Test
    void updateTaskModifiesFieldsAndRespectsDailyBudget() {
        var goal = mockGoal(1, 60);
        when(goalRepository.lockOwned(USER_ID, GOAL_ID)).thenReturn(Optional.of(goal));

        var existing = mockTask(30, false);
        when(taskRepository.findTaskInGoalOwned(USER_ID, GOAL_ID, TASK_ID)).thenReturn(Optional.of(existing));
        // Other tasks on that day use 10 mins
        when(taskRepository.getDailyUsedMinutes(GOAL_ID, LocalDate.of(2026, 11, 10), TASK_ID)).thenReturn(10);

        var updatedStored = new TaskJdbcRepository.StoredTask(
                TASK_ID, GOAL_ID, "CÆ¡ sá»Ÿ dá»¯ liá»‡u", "Ã”n SQL JOIN nÃ¢ng cao",
                LocalDate.of(2026, 11, 10), 45, true, "manual", Instant.now()
        );
        when(taskRepository.findTaskInGoalOwned(USER_ID, GOAL_ID, TASK_ID))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(updatedStored));

        var updateReq = new UpdateTaskRequest("Ã”n SQL JOIN nÃ¢ng cao", null, 45, true);
        SingleTaskResponse res = service.updateTask(USER_ID, GOAL_ID, TASK_ID, updateReq);

        assertEquals("Ã”n SQL JOIN nÃ¢ng cao", res.title());
        assertEquals(45, res.estimatedMinutes());
        assertTrue(res.completed());

        verify(taskRepository).updateTask(TASK_ID, GOAL_ID, "Ã”n SQL JOIN nÃ¢ng cao", LocalDate.of(2026, 11, 10), 45, true);
        verify(taskRepository).incrementGoalVersion(USER_ID, GOAL_ID);
    }

    @Test
    void setTaskCompletionDoesNotIncrementVersionIfAlreadySame() {
        var existing = mockTask(30, true); // already completed
        when(taskRepository.findTaskOwned(USER_ID, TASK_ID)).thenReturn(Optional.of(existing));
        when(goalRepository.lockOwned(USER_ID, GOAL_ID)).thenReturn(Optional.of(mockGoal(2, 60)));

        service.setTaskCompletion(USER_ID, TASK_ID, true);

        verify(taskRepository, never()).updateTask(any(), any(), any(), any(), anyInt(), anyBoolean());
        verify(taskRepository, never()).incrementGoalVersion(any(), any());
    }

    @Test
    void deleteTaskDirectDeletesAndIncrementsVersion() {
        var existing = mockTask(30, false);
        when(taskRepository.findTaskOwned(USER_ID, TASK_ID)).thenReturn(Optional.of(existing));
        when(goalRepository.lockOwned(USER_ID, GOAL_ID)).thenReturn(Optional.of(mockGoal(0, 60)));
        when(taskRepository.findTaskInGoalOwned(USER_ID, GOAL_ID, TASK_ID)).thenReturn(Optional.of(existing));
        when(taskRepository.deleteTask(TASK_ID, GOAL_ID)).thenReturn(true);

        service.deleteTaskDirect(USER_ID, TASK_ID);

        verify(taskRepository).deleteTask(TASK_ID, GOAL_ID);
        verify(taskRepository).incrementGoalVersion(USER_ID, GOAL_ID);
    }

    @Test
    void listTasksByDateCalculatesSummaryCorrectly() {
        var task1 = mockTask(30, true);
        var task2 = new TaskJdbcRepository.StoredTask(
                UUID.randomUUID(), GOAL_ID, "CÆ¡ sá»Ÿ dá»¯ liá»‡u", "Task 2",
                LocalDate.of(2026, 11, 10), 45, false, "manual", Instant.now()
        );

        when(taskRepository.listTasksByUserAndDate(USER_ID, LocalDate.of(2026, 11, 10), 0, 100))
                .thenReturn(List.of(task1, task2));
        when(taskRepository.countTasksByUserAndDate(USER_ID, LocalDate.of(2026, 11, 10))).thenReturn(2L);

        DailyTasksResponse res = service.listTasksByDate(USER_ID, LocalDate.of(2026, 11, 10), 1, 100);

        assertEquals("2026-11-10", res.date());
        assertEquals(75, res.totalEstimatedMinutes());
        assertEquals(1, res.completedCount());
        assertEquals(2, res.totalCount());
        assertEquals(2, res.items().size());
    }

    @Test
    void getGoalProgressCalculatesProgressAccurately() {
        when(goalRepository.findOwned(USER_ID, GOAL_ID)).thenReturn(Optional.of(mockGoal(0, 60)));
        when(taskRepository.getGoalProgress(USER_ID, GOAL_ID)).thenReturn(new TaskJdbcRepository.ProgressCount(4, 3));

        GoalProgressResponse res = service.getGoalProgress(USER_ID, GOAL_ID);

        assertEquals(4, res.totalTasks());
        assertEquals(3, res.completedTasks());
        assertEquals(1, res.remainingTasks());
        assertEquals(0.75, res.completionRate(), 0.0001);
    }

    @Test
    void getGoalProgressHandlesZeroTasksWithoutDivisionByZero() {
        when(goalRepository.findOwned(USER_ID, GOAL_ID)).thenReturn(Optional.of(mockGoal(0, 60)));
        when(taskRepository.getGoalProgress(USER_ID, GOAL_ID)).thenReturn(new TaskJdbcRepository.ProgressCount(0, 0));

        GoalProgressResponse res = service.getGoalProgress(USER_ID, GOAL_ID);

        assertEquals(0, res.totalTasks());
        assertEquals(0, res.completedTasks());
        assertEquals(0, res.remainingTasks());
        assertEquals(0.0, res.completionRate(), 0.0001);
    }
}
