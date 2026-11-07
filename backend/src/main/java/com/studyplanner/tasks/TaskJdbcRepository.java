package com.studyplanner.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Task persistence. study_tasks has no user_id column, so every query joins the parent goal and filters by the
 * trusted owner ID (docs/backend-api-rules.md section 6). SQL fragments are fixed strings; values are parameters.
 */
@Repository
public class TaskJdbcRepository {
    private static final String COLUMNS = """
            t.id, t.goal_id, t.title, t.scheduled_date, t.estimated_minutes, t.completed, t.source, t.created_at
            """;
    private static final String FROM_OWNED = """
            FROM study_tasks t JOIN study_goals g ON g.id = t.goal_id
            """;
    private static final String ORDER = " ORDER BY t.scheduled_date ASC, t.created_at ASC, t.id ASC";

    private static final RowMapper<StoredTask> MAPPER = (rs, rowNum) -> new StoredTask(
            rs.getObject("id", UUID.class),
            rs.getObject("goal_id", UUID.class),
            rs.getString("title"),
            rs.getObject("scheduled_date", LocalDate.class),
            rs.getInt("estimated_minutes"),
            rs.getBoolean("completed"),
            rs.getString("source"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
    );

    private final NamedParameterJdbcTemplate jdbc;

    public TaskJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record StoredTask(UUID id, UUID goalId, String title, LocalDate scheduledDate, int estimatedMinutes,
                             boolean completed, String source, Instant createdAt) {}

    /** Tasks of one owned goal, optionally filtered by day and completion state. */
    public List<StoredTask> listByGoal(UUID trustedOwnerId, UUID goalId, LocalDate date, Boolean completed,
                                       int limit, long offset) {
        var params = goalFilterParams(trustedOwnerId, goalId, date, completed)
                .addValue("limit", limit).addValue("offset", offset);
        return jdbc.query("SELECT " + COLUMNS + FROM_OWNED + goalWhere(date, completed) + ORDER
                + " LIMIT :limit OFFSET :offset", params, MAPPER);
    }

    public long countByGoal(UUID trustedOwnerId, UUID goalId, LocalDate date, Boolean completed) {
        Long total = jdbc.queryForObject("SELECT count(*) " + FROM_OWNED + goalWhere(date, completed),
                goalFilterParams(trustedOwnerId, goalId, date, completed), Long.class);
        return total == null ? 0 : total;
    }

    /** Tasks scheduled on one calendar date across all goals of the owner. */
    public List<StoredTask> listByDate(UUID trustedOwnerId, LocalDate date, int limit, long offset) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(date, "date");
        var params = new MapSqlParameterSource("ownerId", trustedOwnerId).addValue("date", date)
                .addValue("limit", limit).addValue("offset", offset);
        return jdbc.query("SELECT " + COLUMNS + FROM_OWNED
                + " WHERE g.user_id = :ownerId AND t.scheduled_date = :date" + ORDER
                + " LIMIT :limit OFFSET :offset", params, MAPPER);
    }

    public long countByDate(UUID trustedOwnerId, LocalDate date) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(date, "date");
        Long total = jdbc.queryForObject("SELECT count(*) " + FROM_OWNED
                + " WHERE g.user_id = :ownerId AND t.scheduled_date = :date",
                new MapSqlParameterSource("ownerId", trustedOwnerId).addValue("date", date), Long.class);
        return total == null ? 0 : total;
    }

    /** The task must belong to the goal in the URL and the goal to the owner; otherwise it is empty. */
    public Optional<StoredTask> findOwned(UUID trustedOwnerId, UUID goalId, UUID taskId) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(goalId, "goalId");
        Objects.requireNonNull(taskId, "taskId");
        var params = new MapSqlParameterSource("ownerId", trustedOwnerId)
                .addValue("goalId", goalId).addValue("taskId", taskId);
        return jdbc.query("SELECT " + COLUMNS + FROM_OWNED
                + " WHERE g.user_id = :ownerId AND g.id = :goalId AND t.id = :taskId", params, MAPPER)
                .stream().findFirst();
    }

    /** Sets the completed flag of an owned task; returns false when no such task exists for that owner and goal. */
    public boolean updateCompleted(UUID trustedOwnerId, UUID goalId, UUID taskId, boolean completed) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(goalId, "goalId");
        Objects.requireNonNull(taskId, "taskId");
        var params = new MapSqlParameterSource("ownerId", trustedOwnerId)
                .addValue("goalId", goalId).addValue("taskId", taskId).addValue("completed", completed);
        return jdbc.update("""
                UPDATE study_tasks t SET completed = :completed
                  FROM study_goals g
                 WHERE g.id = t.goal_id AND g.user_id = :ownerId AND g.id = :goalId AND t.id = :taskId
                """, params) == 1;
    }

    private static String goalWhere(LocalDate date, Boolean completed) {
        StringBuilder where = new StringBuilder(" WHERE g.user_id = :ownerId AND g.id = :goalId");
        if (date != null) {
            where.append(" AND t.scheduled_date = :date");
        }
        if (completed != null) {
            where.append(" AND t.completed = :completed");
        }
        return where.toString();
    }

    private static MapSqlParameterSource goalFilterParams(UUID owner, UUID goalId, LocalDate date, Boolean completed) {
        requireOwner(owner);
        Objects.requireNonNull(goalId, "goalId");
        var params = new MapSqlParameterSource("ownerId", owner).addValue("goalId", goalId);
        if (date != null) {
            params.addValue("date", date);
        }
        if (completed != null) {
            params.addValue("completed", completed);
        }
        return params;
    }

    private static void requireOwner(UUID trustedOwnerId) {
        Objects.requireNonNull(trustedOwnerId, "Authenticated server-side owner is required");
    }
}
