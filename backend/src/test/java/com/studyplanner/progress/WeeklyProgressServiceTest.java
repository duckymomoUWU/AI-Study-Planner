package com.studyplanner.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WeeklyProgressServiceTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    // 2026-11-08 is a Sunday in Ho Chi Minh City; the week is 2026-11-02 .. 2026-11-08.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private WeeklyProgressJdbcRepository repository;
    private WeeklyProgressService service;

    @BeforeEach
    void setUp() {
        repository = mock(WeeklyProgressJdbcRepository.class);
        service = new WeeklyProgressService(repository, CLOCK);
    }

    private static WeeklyProgressJdbcRepository.DayCount count(String date, long total, long completed) {
        return new WeeklyProgressJdbcRepository.DayCount(LocalDate.parse(date), total, completed);
    }

    @Test
    void anyDateOfTheWeekGivesTheSameMondayToSundayRange() {
        for (String date : List.of("2026-11-02", "2026-11-04", "2026-11-08")) {
            when(repository.countByDay(OWNER, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8))).thenReturn(List.of());
            var week = service.getWeek(OWNER, LocalDate.parse(date));
            assertEquals(LocalDate.of(2026, 11, 2), week.weekStart(), date);
            assertEquals(LocalDate.of(2026, 11, 8), week.weekEnd(), date);
        }
    }

    @Test
    void mondayStartsANewWeekAndSundayEndsTheOldOne() {
        var next = service.getWeek(OWNER, LocalDate.of(2026, 11, 9));
        assertEquals(LocalDate.of(2026, 11, 9), next.weekStart());
        assertEquals(LocalDate.of(2026, 11, 15), next.weekEnd());
        verify(repository).countByDay(OWNER, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15));

        var previous = service.getWeek(OWNER, LocalDate.of(2026, 11, 1)); // a Sunday
        assertEquals(LocalDate.of(2026, 10, 26), previous.weekStart());
        assertEquals(LocalDate.of(2026, 11, 1), previous.weekEnd());
    }

    @Test
    void noDateMeansTheCurrentWeekOfTheServerClock() {
        var week = service.getWeek(OWNER, null);
        assertEquals(LocalDate.of(2026, 11, 2), week.weekStart());
        assertEquals(LocalDate.of(2026, 11, 8), week.weekEnd());
    }

    @Test
    void weeksAcrossAYearOrLeapDayBoundaryAreCalendarWeeks() {
        var yearEnd = service.getWeek(OWNER, LocalDate.of(2027, 1, 1));
        assertEquals(LocalDate.of(2026, 12, 28), yearEnd.weekStart());
        assertEquals(LocalDate.of(2027, 1, 3), yearEnd.weekEnd());

        var leap = service.getWeek(OWNER, LocalDate.of(2028, 2, 29));
        assertEquals(LocalDate.of(2028, 2, 28), leap.weekStart());
        assertEquals(LocalDate.of(2028, 3, 5), leap.weekEnd());
    }

    @Test
    void sumsTheDaysAndAlwaysReturnsSevenDaysWithZeroForDaysWithoutTasks() {
        when(repository.countByDay(any(), any(), any())).thenReturn(List.of(
                count("2026-11-02", 2, 1), count("2026-11-04", 5, 2), count("2026-11-08", 1, 1)));

        var week = service.getWeek(OWNER, LocalDate.of(2026, 11, 4));

        assertEquals(8, week.totalTasks());
        assertEquals(4, week.completedTasks());
        assertEquals(4, week.remainingTasks());
        assertEquals(0.5, week.completionRate());
        assertEquals(7, week.days().size());
        assertEquals(LocalDate.of(2026, 11, 2), week.days().get(0).date());
        assertEquals(LocalDate.of(2026, 11, 8), week.days().get(6).date());
        assertEquals(2, week.days().get(0).totalTasks());
        assertEquals(0, week.days().get(1).totalTasks());
        assertEquals(5, week.days().get(2).totalTasks());
        assertEquals(2, week.days().get(2).completedTasks());
    }

    @Test
    void aWeekWithoutTasksIsZeroOfZeroWithRateZeroNotNaN() {
        when(repository.countByDay(any(), any(), any())).thenReturn(List.of());
        var week = service.getWeek(OWNER, LocalDate.of(2026, 11, 4));
        assertEquals(0, week.totalTasks());
        assertEquals(0, week.completedTasks());
        assertEquals(0, week.remainingTasks());
        assertEquals(0.0, week.completionRate());
        assertTrue(!Double.isNaN(week.completionRate()));
        assertEquals(7, week.days().size());
    }

    @Test
    void aFullyDoneWeekIsRateOne() {
        when(repository.countByDay(any(), any(), any())).thenReturn(List.of(count("2026-11-03", 3, 3)));
        var week = service.getWeek(OWNER, LocalDate.of(2026, 11, 3));
        assertEquals(1.0, week.completionRate());
        assertEquals(0, week.remainingTasks());
    }

    @Test
    void datesOutsideTheSupportedRangeAreRefusedWithoutQuerying() {
        assertThrows(WeeklyProgressService.DateOutOfRangeException.class,
                () -> service.getWeek(OWNER, LocalDate.of(1999, 12, 31)));
        assertThrows(WeeklyProgressService.DateOutOfRangeException.class,
                () -> service.getWeek(OWNER, LocalDate.of(2101, 1, 1)));
        assertThrows(WeeklyProgressService.DateOutOfRangeException.class,
                () -> service.getWeek(OWNER, LocalDate.MAX));
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void refusesACallWithoutATrustedOwner() {
        assertThrows(NullPointerException.class, () -> service.getWeek(null, LocalDate.of(2026, 11, 4)));
    }
}
