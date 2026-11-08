package com.studyplanner.progress;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Progress of one calendar week (Monday to Sunday) = completed tasks / total tasks scheduled in that week, over all
 * of the user's goals. It says nothing about how well the student understands the material. A week without tasks is
 * 0 of 0 with rate 0, not an error.
 */
@Service
public class WeeklyProgressService {
    static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);

    private final WeeklyProgressJdbcRepository repository;
    private final Clock clock;

    public WeeklyProgressService(WeeklyProgressJdbcRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public record Day(LocalDate date, long totalTasks, long completedTasks) {}

    public record WeeklyProgress(LocalDate weekStart, LocalDate weekEnd, long totalTasks, long completedTasks,
                                 long remainingTasks, double completionRate, List<Day> days) {}

    /** The requested date is outside the supported range. */
    public static final class DateOutOfRangeException extends RuntimeException {
        public DateOutOfRangeException() {
            super("Date out of range");
        }
    }

    /**
     * @param anyDateInWeek any date of the wanted week; null means the current week (today in the server's
     *                      default time zone)
     */
    @Transactional(readOnly = true)
    public WeeklyProgress getWeek(UUID trustedOwnerId, LocalDate anyDateInWeek) {
        Objects.requireNonNull(trustedOwnerId, "Authenticated server-side owner is required");
        LocalDate date = anyDateInWeek != null ? anyDateInWeek : LocalDate.now(clock);
        if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) {
            throw new DateOutOfRangeException();
        }
        LocalDate start = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);

        Map<LocalDate, WeeklyProgressJdbcRepository.DayCount> counted = repository.countByDay(trustedOwnerId, start, end)
                .stream().collect(Collectors.toMap(WeeklyProgressJdbcRepository.DayCount::date, Function.identity()));

        List<Day> days = new ArrayList<>(7);
        long total = 0;
        long completed = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate day = start.plusDays(i);
            var c = counted.get(day);
            long dayTotal = c == null ? 0 : c.total();
            long dayCompleted = c == null ? 0 : c.completed();
            days.add(new Day(day, dayTotal, dayCompleted));
            total += dayTotal;
            completed += dayCompleted;
        }
        double rate = total == 0 ? 0.0 : (double) completed / total;
        return new WeeklyProgress(start, end, total, completed, total - completed, rate, List.copyOf(days));
    }
}
