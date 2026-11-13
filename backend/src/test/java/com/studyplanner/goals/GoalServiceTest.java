package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private static final Instant CREATED = Instant.parse("2026-11-07T03:00:00Z");

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
                90, "Asia/Ho_Chi_Minh", version, CREATED);
    }

    private static GoalPatch patch(Integer minutes, LocalDate exam, String timezone) {
        return new GoalPatch(null, null, null, exam, minutes, timezone);
    }

    @Test
    void createNormalizesInputAndUsesVietnamLocalDateAndServerTimezone() {
        when(repository.insertOwned(eq(OWNER), any(GoalPolicy.GoalInput.class), eq("Asia/Ho_Chi_Minh")))
                .thenReturn(stored(0));

        assertEquals(stored(0), service.createGoal(OWNER, input(), null));

        var captor = ArgumentCaptor.forClass(GoalPolicy.GoalInput.class);
        verify(repository).insertOwned(eq(OWNER), captor.capture(), eq("Asia/Ho_Chi_Minh"));
        assertEquals("Toán", captor.getValue().subject());
        assertEquals("Giải tích", captor.getValue().topics());
        assertEquals(LocalDate.of(2026, 11, 7), captor.getValue().startDate());
    }

    @Test
    void createAcceptsAnIanaTimezoneAndStoresIt() {
        when(repository.insertOwned(eq(OWNER), any(), eq("America/New_York"))).thenReturn(stored(0));
        service.createGoal(OWNER, input(), "America/New_York");
        verify(repository).insertOwned(eq(OWNER), any(), eq("America/New_York"));
    }

    @Test
    void createRejectsYesterdayInVietnamEvenWhenItIsStillTodayInUtc() {
        var input = new GoalPolicy.GoalInput("Math", "Algebra", LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 12, 5), 60);
        var ex = assertThrows(GoalService.GoalValidationException.class,
                () -> service.createGoal(OWNER, input, null));
        assertTrue(ex.violations().stream().anyMatch(v -> v.field().equals("startDate")
                && v.code().equals("must_not_be_in_past")));
        verify(repository, never()).insertOwned(any(), any(), any());
    }

    @Test
    void patchPreservesHistoricalStartOfAnOngoingGoal() {
        service = new GoalService(repository, Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneOffset.UTC),
                "Asia/Ho_Chi_Minh");
        when(repository.lockOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(0)));
        when(repository.listScheduledTasksOwned(OWNER, GOAL)).thenReturn(List.of());
        when(repository.updateOwnedIfVersion(eq(OWNER), eq(GOAL), any(), eq("Asia/Ho_Chi_Minh"), eq(0)))
                .thenReturn(true);
        when(repository.findOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(1)));
        service.patchGoal(OWNER, GOAL, patch(120, null, null), 0);
        var merged = ArgumentCaptor.forClass(GoalPolicy.GoalInput.class);
        verify(repository).updateOwnedIfVersion(eq(OWNER), eq(GOAL), merged.capture(),
                eq("Asia/Ho_Chi_Minh"), eq(0));
        assertEquals(LocalDate.of(2026, 11, 7), merged.getValue().startDate());
    }

    @Test
    void createRejectsUnknownOrTooLongTimezone() {
        for (String bad : List.of("Mars/Olympus", "x".repeat(65), "")) {
            var ex = assertThrows(GoalService.GoalValidationException.class,
                    () -> service.createGoal(OWNER, input(), bad));
            assertTrue(ex.violations().stream()
                    .anyMatch(v -> v.field().equals("timezone") && v.code().equals("invalid_timezone")));
        }
        verify(repository, never()).insertOwned(any(), any(), any());
    }

    @Test
    void createUsesVietnamLocalDateRatherThanUtcDateForPastExamValidation() {
        // 2026-11-06 17:30 UTC is already 2026-11-07 in Ho Chi Minh City.
        var examThatWasYesterdayLocally = new GoalPolicy.GoalInput("Toán", "Giải tích",
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6), 60);

        var exception = assertThrows(GoalService.GoalValidationException.class,
                () -> service.createGoal(OWNER, examThatWasYesterdayLocally, null));

        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("must_not_be_in_past")));
        verify(repository, never()).insertOwned(any(), any(), any());
    }

    @Test
    void createRejectsInvalidGoalBeforeWriting() {
        var invalid = new GoalPolicy.GoalInput(" ", "topic", LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 7), 14);

        var exception = assertThrows(GoalService.GoalValidationException.class,
                () -> service.createGoal(OWNER, invalid, null));

        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("required")));
        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("must_be_after_start_date")));
        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("out_of_range")));
        verify(repository, never()).insertOwned(any(), any(), any());
    }

    @Test
    void listIsScopedToTheOwnerAndPaged() {
        when(repository.listOwnedPage(OWNER, 20, 40)).thenReturn(List.of(stored(0)));
        when(repository.countOwned(OWNER)).thenReturn(41L);

        var page = service.listGoals(OWNER, 3, 20);

        assertEquals(List.of(stored(0)), page.items());
        assertEquals(41L, page.totalItems());
        assertThrows(NullPointerException.class, () -> service.listGoals(null, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.listGoals(OWNER, 0, 20));
    }

    @Test
    void getReturnsNotFoundForUnknownOrNotOwnedGoal() {
        when(repository.findOwned(OWNER, GOAL)).thenReturn(Optional.empty());
        assertThrows(GoalService.GoalNotFoundException.class, () -> service.getGoal(OWNER, GOAL));
    }

    @Test
    void patchRejectsStaleVersionBeforeReadingTasks() {
        when(repository.lockOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(2)));

        assertThrows(GoalService.GoalConflictException.class,
                () -> service.patchGoal(OWNER, GOAL, patch(60, null, null), 1));

        verify(repository, never()).listScheduledTasksOwned(any(), any());
        verify(repository, never()).updateOwnedIfVersion(any(), any(), any(), any(), anyInt());
    }

    @Test
    void patchRejectsTasksOnExamDayAndDoesNotWrite() {
        when(repository.lockOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(0)));
        when(repository.listScheduledTasksOwned(OWNER, GOAL)).thenReturn(
                List.of(new GoalPolicy.ScheduledTask(LocalDate.of(2026, 12, 5), 30)));

        var exception = assertThrows(GoalService.GoalValidationException.class,
                () -> service.patchGoal(OWNER, GOAL, patch(null, LocalDate.of(2026, 12, 5), null), 0));

        assertTrue(exception.violations().stream().anyMatch(v -> v.code().equals("task_on_or_after_exam_date")));
        verify(repository, never()).updateOwnedIfVersion(any(), any(), any(), any(), anyInt());
    }

    @Test
    void patchMergesOnlySentFieldsValidatesTasksThenUsesOptimisticVersion() {
        when(repository.lockOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(0)));
        when(repository.listScheduledTasksOwned(OWNER, GOAL)).thenReturn(
                List.of(new GoalPolicy.ScheduledTask(LocalDate.of(2026, 11, 8), 45)));
        when(repository.updateOwnedIfVersion(eq(OWNER), eq(GOAL), any(), eq("Asia/Ho_Chi_Minh"), eq(0)))
                .thenReturn(true);
        when(repository.findOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(1)));

        assertEquals(stored(1), service.patchGoal(OWNER, GOAL, patch(120, null, null), 0));

        var merged = ArgumentCaptor.forClass(GoalPolicy.GoalInput.class);
        verify(repository).updateOwnedIfVersion(eq(OWNER), eq(GOAL), merged.capture(),
                eq("Asia/Ho_Chi_Minh"), eq(0));
        assertEquals(120, merged.getValue().minutesPerDay());
        assertEquals("Toán", merged.getValue().subject());
        assertEquals(LocalDate.of(2026, 12, 5), merged.getValue().examDate());
        verify(repository).lockOwned(OWNER, GOAL);
    }

    @Test
    void patchCanChangeTimezoneAndRejectsAnInvalidOne() {
        when(repository.lockOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(0)));
        when(repository.listScheduledTasksOwned(OWNER, GOAL)).thenReturn(List.of());
        when(repository.updateOwnedIfVersion(eq(OWNER), eq(GOAL), any(), eq("UTC"), eq(0))).thenReturn(true);
        when(repository.findOwned(OWNER, GOAL)).thenReturn(Optional.of(stored(1)));
        service.patchGoal(OWNER, GOAL, patch(null, null, "UTC"), 0);
        verify(repository).updateOwnedIfVersion(eq(OWNER), eq(GOAL), any(), eq("UTC"), eq(0));

        var ex = assertThrows(GoalService.GoalValidationException.class,
                () -> service.patchGoal(OWNER, GOAL, patch(null, null, "Nowhere/Land"), 0));
        assertTrue(ex.violations().stream().anyMatch(v -> v.code().equals("invalid_timezone")));
    }

    @Test
    void deleteReturnsNotFoundWhenNoOwnedGoalWasDeleted() {
        when(repository.deleteOwned(OWNER, GOAL)).thenReturn(false);
        assertThrows(GoalService.GoalNotFoundException.class, () -> service.deleteGoal(OWNER, GOAL));
    }
}
