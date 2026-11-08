package com.studyplanner.progress;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Task counts per calendar day for the tasks of one user. study_tasks has no user_id column, so the query joins
 * the parent goal and filters by the trusted owner ID. Drafts (plan_drafts) are not tasks and are never counted.
 */
@Repository
public class WeeklyProgressJdbcRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public WeeklyProgressJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Counts of the tasks scheduled on one day; days without tasks are simply absent. */
    public record DayCount(LocalDate date, long total, long completed) {}

    /** Tasks scheduled from {@code from} to {@code to}, both included, grouped by day in date order. */
    public List<DayCount> countByDay(UUID trustedOwnerId, LocalDate from, LocalDate to) {
        Objects.requireNonNull(trustedOwnerId, "Authenticated server-side owner is required");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        var params = new MapSqlParameterSource("ownerId", trustedOwnerId)
                .addValue("from", from).addValue("to", to);
        return jdbc.query("""
                SELECT t.scheduled_date AS day, count(*) AS total,
                       count(*) FILTER (WHERE t.completed) AS completed
                  FROM study_tasks t JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :ownerId AND t.scheduled_date BETWEEN :from AND :to
                 GROUP BY t.scheduled_date
                 ORDER BY t.scheduled_date
                """, params, (rs, rowNum) -> new DayCount(
                rs.getObject("day", LocalDate.class), rs.getLong("total"), rs.getLong("completed")));
    }
}
