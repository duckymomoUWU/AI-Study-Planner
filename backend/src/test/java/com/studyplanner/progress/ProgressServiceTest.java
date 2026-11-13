package com.studyplanner.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.studyplanner.goals.GoalService;

class ProgressServiceTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GOAL = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private ProgressJdbcRepository repository;
    private ProgressService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProgressJdbcRepository.class);
        service = new ProgressService(repository);
    }

    private void counts(long total, long completed) {
        when(repository.countOwned(OWNER, GOAL))
                .thenReturn(Optional.of(new ProgressJdbcRepository.TaskCounts(total, completed)));
    }

    @Test
    void remainingIsTotalMinusCompletedAndRateIsTheirRatio() {
        counts(8, 2);
        var p = service.getProgress(OWNER, GOAL);
        assertEquals(GOAL, p.goalId());
        assertEquals(8, p.totalTasks());
        assertEquals(2, p.completedTasks());
        assertEquals(6, p.remainingTasks());
        assertEquals(0.25, p.completionRate());
    }

    @Test
    void noTasksIsZeroOfZeroWithRateZeroNotNaN() {
        counts(0, 0);
        var p = service.getProgress(OWNER, GOAL);
        assertEquals(0, p.totalTasks());
        assertEquals(0, p.remainingTasks());
        assertEquals(0.0, p.completionRate());
        assertEquals(false, Double.isNaN(p.completionRate()));
    }

    @Test
    void everythingDoneIsRateOneAndNothingRemaining() {
        counts(5, 5);
        var p = service.getProgress(OWNER, GOAL);
        assertEquals(1.0, p.completionRate());
        assertEquals(0, p.remainingTasks());
    }

    @Test
    void aGoalThatIsNotTheCallersIsNotFound() {
        when(repository.countOwned(OWNER, GOAL)).thenReturn(Optional.empty());
        assertThrows(GoalService.GoalNotFoundException.class, () -> service.getProgress(OWNER, GOAL));
    }

    @Test
    void refusesACallWithoutATrustedOwner() {
        assertThrows(NullPointerException.class, () -> service.getProgress(null, GOAL));
    }
}
