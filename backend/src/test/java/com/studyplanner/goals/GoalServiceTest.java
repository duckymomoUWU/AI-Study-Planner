package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;

class GoalServiceTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GOAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T17:30:00Z"), ZoneOffset.UTC);

    private GoalJdbcRepository repository;
    private GoalService service;

    @BeforeEach
    void setUp() {
        repository = mock(GoalJdbcRepository.class);
        service = new GoalService(repository, CLOCK, "Asia/Ho_Chi_Minh");
    }

    private GoalPolicy.GoalInput input() {
        return new GoalPolicy.GoalInput("  Toán  ", "  Giải tích  ",
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 12, 5), 90);
    }

    private GoalJdbcRepository.StoredGoal stored(int version) {
        return new GoalJdbcRepository.StoredGoal(GOAL, OWNER, "Toán", "Giải tích",
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 12, 5),
                90, "Asia/Ho_Chi_Minh", version);
    }

    @Test
    void createNormalizesInputAndUsesVietnamLocalDateAndServerTimezone() {
        when(repository.insertOwned(eq(OWNER), any(GoalPolicy.GoalInput.class), eq("Asia/Ho_Chi_Minh")))
                .thenReturn(stored(0));

        assertEquals(stored(0), service.createGoal(OWNER, input()));

        var captor = ArgumentCaptor.forClass(GoalPolicy.GoalInput.class);
        verify(repository).insertOwned(eq(OWNER), captor.capture(), eq("Asia/Ho_Chi_Minh"));
        assertEquals("Toán", captor.getValue().subject());
        assertEquals("Giải tích", captor.getValue().topics());
        assertEquals(LocalDate.of(2026, 11, 7), captor.getValue().startDate());
    }

    @Test
    void createUsesVietnamLocalDateRatherThanUtcDateForPastExamValidation() {
        // 2026-11-06 17:30 UTC is already 2026-11-07 in Ho Chi Minh City.
        var examThatWasYesterdayLocally = new GoalPolicy.GoalInput("Toán", "Giải tích",
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6), 60);

        var exception = assertThrows(GoalService.GoalValidationException.class,
                () -> service.createGoal(OWNER, examThatWasYesterdayLocally));

        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("past_date")));
        verify(repository, never()).insertOwned(any(), any(), any());
    }

    @Test
    void createRejectsInvalidGoalBeforeWriting() {
        var invalid = new GoalPolicy.GoalInput(" ", "topic", LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 7), 14);

        var exception = assertThrows(GoalService.GoalValidationException.class,
                () -> service.createGoal(OWNER, invalid));

        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("required")));
        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("not_after_start")));
        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("out_of_range")));
        verify(repository, never()).insertOwned(any(), any(), any());
    }

    @Test
    void listRequiresTrustedOwnerAndDelegatesScopedRead() {
        when(repository.listOwned(OWNER)).thenReturn(List.of(stored(0)));
        assertEquals(List.of(stored(0)), service.listGoals(OWNER));
        verify(repository).listOwned(OWNER);
        assertThrows(NullPointerException.class, () -> service.listGoals(null));
    }

    @Test
    void getReturnsNotFoundForUnknownOrNotOwnedGoal() {
        when(repository.findOwned(OWNER, GOAL)).thenReturn(Optional.empty());
        assertThrows(GoalService.GoalNotFoundException.class, () -> service.getGoal(OWNER, GOAL));
    }

    @Test
    void updateRejectsStaleVersionBeforeReadingTasks() {
        when(repository.lockOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(2)));

        assertThrows(GoalService.GoalConflictException.class,
                () -> service.updateGoal(OWNER, GOAL, input(), 1));

        verify(repository, never()).listScheduledTasksOwned(any(), any());
        verify(repository, never()).updateOwnedIfVersion(any(), any(), any(), any(int.class));
    }

    @Test
    void updateRejectsTasksOnExamDayAndDoesNotWrite() {
        when(repository.lockOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(0)));
        when(repository.listScheduledTasksOwned(OWNER, GOAL)).thenReturn(
                List.of(new GoalPolicy.ScheduledTask(LocalDate.of(2026, 12, 5), 30)));

        var exception = assertThrows(GoalService.GoalValidationException.class,
                () -> service.updateGoal(OWNER, GOAL, input(), 0));

        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("task_after_deadline")));
        verify(repository, never()).updateOwnedIfVersion(any(), any(), any(), any(int.class));
    }

    @Test
    void updateValidatesTasksThenUsesOptimisticVersionAndReturnsFreshGoal() {
        when(repository.lockOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(0)));
        when(repository.listScheduledTasksOwned(OWNER, GOAL)).thenReturn(
                List.of(new GoalPolicy.ScheduledTask(LocalDate.of(2026, 11, 8), 45)));
        when(repository.updateOwnedIfVersion(eq(OWNER), eq(GOAL), any(), eq(0))).thenReturn(true);
        when(repository.findOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(1)));

        assertEquals(stored(1), service.updateGoal(OWNER, GOAL, input(), 0));
        verify(repository).lockOwned(OWNER, GOAL);
        verify(repository).listScheduledTasksOwned(OWNER, GOAL);
        verify(repository).updateOwnedIfVersion(eq(OWNER), eq(GOAL), any(), eq(0));
    }

    @Test
    void deleteReturnsNotFoundWhenNoOwnedGoalWasDeleted() {
        when(repository.deleteOwned(OWNER, GOAL)).thenReturn(false);
        assertThrows(GoalService.GoalNotFoundException.class, () -> service.deleteGoal(OWNER, GOAL));
    }
}
