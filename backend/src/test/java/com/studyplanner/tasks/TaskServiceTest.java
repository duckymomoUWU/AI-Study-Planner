package com.studyplanner.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.studyplanner.goals.GoalJdbcRepository;
import com.studyplanner.goals.GoalService;

class TaskServiceTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GOAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TASK = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private TaskJdbcRepository tasks;
    private GoalJdbcRepository goals;
    private TaskService service;

    @BeforeEach
    void setUp() {
        tasks = mock(TaskJdbcRepository.class);
        goals = mock(GoalJdbcRepository.class);
        service = new TaskService(tasks, goals, mock(com.studyplanner.goals.StudyBudgetService.class));
        when(goals.lockOwned(OWNER, GOAL)).thenReturn(Optional.of(mock(GoalJdbcRepository.StoredGoal.class)));
    }

    private static TaskJdbcRepository.StoredTask task(boolean completed) {
        return new TaskJdbcRepository.StoredTask(TASK, GOAL, "Ôn SQL", LocalDate.of(2026, 11, 10), 30, completed,
                "manual", Instant.parse("2026-11-07T03:00:00Z"));
    }

    @Test
    void realChangeLocksTheGoalWritesAndBumpsTheVersionOnce() {
        when(tasks.findOwned(OWNER, GOAL, TASK)).thenReturn(Optional.of(task(false)), Optional.of(task(true)));
        when(tasks.updateCompleted(OWNER, GOAL, TASK, true)).thenReturn(true);

        var result = service.setCompleted(OWNER, GOAL, TASK, true);

        assertEquals(true, result.completed());
        var order = inOrder(goals, tasks);
        order.verify(goals).lockOwned(OWNER, GOAL);
        order.verify(tasks).updateCompleted(OWNER, GOAL, TASK, true);
        order.verify(goals).bumpVersion(OWNER, GOAL);
        verify(goals, times(1)).bumpVersion(OWNER, GOAL);
    }

    @Test
    void askingForTheStateTheTaskAlreadyHasChangesNothingAndKeepsTheVersion() {
        when(tasks.findOwned(OWNER, GOAL, TASK)).thenReturn(Optional.of(task(true)));

        assertEquals(task(true), service.setCompleted(OWNER, GOAL, TASK, true));

        verify(tasks, never()).updateCompleted(any(), any(), any(), anyBoolean());
        verify(goals, never()).bumpVersion(any(), any());
    }

    @Test
    void reopeningIsARealChangeToo() {
        when(tasks.findOwned(OWNER, GOAL, TASK)).thenReturn(Optional.of(task(true)), Optional.of(task(false)));
        when(tasks.updateCompleted(OWNER, GOAL, TASK, false)).thenReturn(true);

        assertEquals(false, service.setCompleted(OWNER, GOAL, TASK, false).completed());
        verify(goals).bumpVersion(OWNER, GOAL);
    }

    @Test
    void aGoalTheCallerDoesNotOwnIsNotFoundAndNothingIsTouched() {
        when(goals.lockOwned(OWNER, GOAL)).thenReturn(Optional.empty());

        assertThrows(GoalService.GoalNotFoundException.class, () -> service.setCompleted(OWNER, GOAL, TASK, true));

        verify(tasks, never()).updateCompleted(any(), any(), any(), anyBoolean());
        verify(goals, never()).bumpVersion(any(), any());
    }

    @Test
    void aTaskThatIsNotInThatGoalIsNotFound() {
        when(tasks.findOwned(OWNER, GOAL, TASK)).thenReturn(Optional.empty());

        assertThrows(TaskService.TaskNotFoundException.class, () -> service.setCompleted(OWNER, GOAL, TASK, true));

        verify(goals, never()).bumpVersion(any(), any());
    }

    @Test
    void listByGoalRefusesAGoalTheCallerDoesNotOwnEvenWhenItWouldBeEmpty() {
        when(goals.findOwned(OWNER, GOAL)).thenReturn(Optional.empty());
        assertThrows(GoalService.GoalNotFoundException.class,
                () -> service.listByGoal(OWNER, GOAL, null, null, 1, 20));
        verify(tasks, never()).listByGoal(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void listsAreOwnerScopedAndPaged() {
        when(goals.findOwned(OWNER, GOAL)).thenReturn(Optional.of(mock(GoalJdbcRepository.StoredGoal.class)));
        when(tasks.listByGoal(OWNER, GOAL, null, false, 20, 40)).thenReturn(List.of(task(false)));
        when(tasks.countByGoal(OWNER, GOAL, null, false)).thenReturn(41L);
        var page = service.listByGoal(OWNER, GOAL, null, false, 3, 20);
        assertEquals(41L, page.totalItems());
        assertEquals(List.of(task(false)), page.items());

        var day = LocalDate.of(2026, 11, 10);
        when(tasks.listByDate(OWNER, day, 20, 0)).thenReturn(List.of(task(true)));
        when(tasks.countByDate(OWNER, day)).thenReturn(1L);
        assertEquals(1L, service.listByDate(OWNER, day, 1, 20).totalItems());

        assertThrows(NullPointerException.class, () -> service.listByDate(null, day, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.listByDate(OWNER, day, 0, 20));
    }
}
