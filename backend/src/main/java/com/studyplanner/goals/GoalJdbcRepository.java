package com.studyplanner.goals;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * ASP-23 persistence primitives. All reads/writes are restricted to a trusted server-side owner ID.
 * This class exposes no HTTP endpoint; ASP-12 must provide authenticated identity before wiring a controller.
 */
@Repository
public class GoalJdbcRepository {
    private static final String GOAL_COLUMNS = """
            id, user_id, subject, topics, start_date, exam_date,
            minutes_per_day, timezone, version
            """;

    private static final RowMapper<StoredGoal> GOAL_MAPPER = (rs, rowNum) -> new StoredGoal(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("subject"),
            rs.getString("topics"),
            rs.getObject("start_date", LocalDate.class),
            rs.getObject("exam_date", LocalDate.class),
            rs.getInt("minutes_per_day"),
            rs.getString("timezone"),
            rs.getInt("version")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public GoalJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Includes owner ID only inside backend domain code, never in the public API response. */
    public record StoredGoal(UUID id, UUID ownerId, String subject, String topics,
                             LocalDate startDate, LocalDate examDate, int minutesPerDay,
                             String timezone, int version) {}

    public List<StoredGoal> listOwned(UUID trustedOwnerId) {
        requireOwner(trustedOwnerId);
        return jdbc.query(
                "SELECT " + GOAL_COLUMNS + " FROM study_goals WHERE user_id = :ownerId ORDER BY created_at DESC, id DESC",
                owner(trustedOwnerId),
                GOAL_MAPPER
        );
    }

    public Optional<StoredGoal> findOwned(UUID trustedOwnerId, UUID goalId) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(goalId, "goalId");
        var params = owner(trustedOwnerId).addValue("goalId", goalId);
        return jdbc.query(
                "SELECT " + GOAL_COLUMNS + " FROM study_goals WHERE user_id = :ownerId AND id = :goalId",
                params, GOAL_MAPPER
        ).stream().findFirst();
    }

    /** Call inside the same transaction that validates existing tasks and updates the goal. */
    public Optional<StoredGoal> lockOwned(UUID trustedOwnerId, UUID goalId) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(goalId, "goalId");
        var params = owner(trustedOwnerId).addValue("goalId", goalId);
        return jdbc.query(
                "SELECT " + GOAL_COLUMNS + " FROM study_goals WHERE user_id = :ownerId AND id = :goalId FOR UPDATE",
                params, GOAL_MAPPER
        ).stream().findFirst();
    }

    /** The caller must validate and normalize GoalInput first. Timezone is chosen by the server. */
    public StoredGoal insertOwned(UUID trustedOwnerId, GoalPolicy.GoalInput input, String serverTimezone) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(serverTimezone, "serverTimezone");
        var params = goalParams(trustedOwnerId, input)
                .addValue("timezone", serverTimezone);
        var rows = jdbc.query(
                "INSERT INTO study_goals (user_id, subject, topics, start_date, exam_date, minutes_per_day, timezone) "
                + "VALUES (:ownerId, :subject, :topics, :startDate, :examDate, :minutesPerDay, :timezone) "
                + "RETURNING " + GOAL_COLUMNS,
                params, GOAL_MAPPER
        );
        if (rows.size() != 1) {
            throw new IllegalStateException("Goal INSERT did not return exactly one row.");
        }
        return rows.getFirst();
    }

    /**
     * Returns false for missing / not-owned goals and stale version.
     * Call within the same transaction as lockOwned and task policy validation.
     */
    public boolean updateOwnedIfVersion(UUID trustedOwnerId, UUID goalId,
                                         GoalPolicy.GoalInput input, int expectedVersion) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(goalId, "goalId");
        Objects.requireNonNull(input, "input");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be >= 0");
        }
        var params = goalParams(trustedOwnerId, input)
                .addValue("goalId", goalId)
                .addValue("expectedVersion", expectedVersion);
        return jdbc.update(
                """
                UPDATE study_goals
                   SET subject = :subject, topics = :topics, start_date = :startDate,
                       exam_date = :examDate, minutes_per_day = :minutesPerDay,
                       version = version + 1
                 WHERE user_id = :ownerId AND id = :goalId AND version = :expectedVersion
                """,
                params
        ) == 1;
    }

    /** Database V1 cascade removes tasks and drafts; UI confirmation must happen before calling. */
    public boolean deleteOwned(UUID trustedOwnerId, UUID goalId) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(goalId, "goalId");
        return jdbc.update(
                "DELETE FROM study_goals WHERE user_id = :ownerId AND id = :goalId",
                owner(trustedOwnerId).addValue("goalId", goalId)
        ) == 1;
    }

    /** Tasks are filtered via the goal's ownership, even though study_tasks has no user_id column. */
    public List<GoalPolicy.ScheduledTask> listScheduledTasksOwned(UUID trustedOwnerId, UUID goalId) {
        requireOwner(trustedOwnerId);
        Objects.requireNonNull(goalId, "goalId");
        return jdbc.query(
                """
                SELECT t.scheduled_date, t.estimated_minutes
                  FROM study_tasks t
                  JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :ownerId AND g.id = :goalId
                """,
                owner(trustedOwnerId).addValue("goalId", goalId),
                (rs, rowNum) -> new GoalPolicy.ScheduledTask(
                        rs.getObject("scheduled_date", LocalDate.class),
                        rs.getInt("estimated_minutes")
                )
        );
    }

    private static MapSqlParameterSource goalParams(UUID ownerId, GoalPolicy.GoalInput input) {
        return owner(ownerId)
                .addValue("subject", input.subject())
                .addValue("topics", input.topics())
                .addValue("startDate", input.startDate())
                .addValue("examDate", input.examDate())
                .addValue("minutesPerDay", input.minutesPerDay());
    }

    private static MapSqlParameterSource owner(UUID trustedOwnerId) {
        return new MapSqlParameterSource("ownerId", trustedOwnerId);
    }

    private static void requireOwner(UUID trustedOwnerId) {
        Objects.requireNonNull(trustedOwnerId, "Authenticated server-side owner is required");
    }
}
