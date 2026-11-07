package com.studyplanner.progress;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Task counts of one goal. One query counts the goal's saved tasks, so both numbers come from the same snapshot.
 * The goal is matched together with the trusted owner ID (docs/backend-api-rules.md section 6); drafts are not
 * tasks and are never counted.
 */
@Repository
public class ProgressJdbcRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ProgressJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TaskCounts(long total, long completed) {}

    /** Empty when the goal does not exist or belongs to someone else; a goal without tasks gives 0 and 0. */
    public Optional<TaskCounts> countOwned(UUID trustedOwnerId, UUID goalId) {
        Objects.requireNonNull(trustedOwnerId, "Authenticated server-side owner is required");
        Objects.requireNonNull(goalId, "goalId");
        var params = new MapSqlParameterSource("ownerId", trustedOwnerId).addValue("goalId", goalId);
        return jdbc.query("""
                SELECT count(t.id) AS total, count(t.id) FILTER (WHERE t.completed) AS completed
                  FROM study_goals g LEFT JOIN study_tasks t ON t.goal_id = g.id
                 WHERE g.user_id = :ownerId AND g.id = :goalId
                 GROUP BY g.id
                """, params, (rs, rowNum) -> new TaskCounts(rs.getLong("total"), rs.getLong("completed")))
                .stream().findFirst();
    }
}
