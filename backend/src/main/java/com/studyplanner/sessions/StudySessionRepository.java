package com.studyplanner.sessions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Study-session persistence. Every query filters by the trusted owner ID; study_tasks has no user column,
 * so tasks are checked through their goal (docs/backend-api-rules.md section 6).
 */
@Repository
public class StudySessionRepository {
    private static final String COLUMNS = """
            id, user_id, task_id, goal_id, task_title_snapshot, subject_snapshot, status,
            accumulated_focus_seconds, started_at, last_resumed_at, ended_at, version
            """;

    private static final RowMapper<Stored> MAPPER = (rs, n) -> new Stored(
            rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
            rs.getObject("task_id", UUID.class), rs.getObject("goal_id", UUID.class),
            rs.getString("task_title_snapshot"), rs.getString("subject_snapshot"),
            StudySessionPolicy.Status.valueOf(rs.getString("status")),
            rs.getInt("accumulated_focus_seconds"), instant(rs.getObject("started_at", OffsetDateTime.class)),
            instant(rs.getObject("last_resumed_at", OffsetDateTime.class)),
            instant(rs.getObject("ended_at", OffsetDateTime.class)), rs.getInt("version"));

    private final NamedParameterJdbcTemplate jdbc;

    public StudySessionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Stored(UUID id, UUID userId, UUID taskId, UUID goalId, String taskTitle, String subject,
                         StudySessionPolicy.Status status, int accumulatedSeconds, Instant startedAt,
                         Instant lastResumedAt, Instant endedAt, int version) {}

    public record TaskRef(UUID taskId, UUID goalId, String title, String subject) {}

    public record Receipt(String fingerprint, String responseJson) {}

    public record StudyTime(long completedSessionCount, long totalFocusSeconds) {}
    public record TaskStudyTime(UUID taskId, long completedSessionCount, long totalFocusSeconds) {}

    public List<TaskStudyTime> taskStudyTimes(UUID owner, List<UUID> taskIds) {
        return jdbc.query("""
                SELECT t.id, count(s.id) AS sessions,
                       COALESCE(sum(s.accumulated_focus_seconds),0) AS seconds
                  FROM study_tasks t JOIN study_goals g ON g.id=t.goal_id
                  LEFT JOIN study_sessions s ON s.task_id=t.id AND s.user_id=:owner AND s.status='COMPLETED'
                 WHERE g.user_id=:owner AND t.id IN (:tasks)
                 GROUP BY t.id ORDER BY t.id
                """, new MapSqlParameterSource("owner", owner).addValue("tasks", taskIds),
                (rs,n) -> new TaskStudyTime(rs.getObject("id",UUID.class),rs.getLong("sessions"),rs.getLong("seconds")));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime odt(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /** Serializes this owner's session writes; same lock and order as the shared study budget. */
    public void lockOwner(UUID owner) {
        jdbc.queryForList("SELECT id FROM app_users WHERE id = :owner FOR NO KEY UPDATE",
                new MapSqlParameterSource("owner", owner));
    }

    public Optional<Receipt> receipt(UUID owner, UUID key) {
        return jdbc.query("SELECT fingerprint, response::text AS response FROM study_session_receipts "
                        + "WHERE user_id = :owner AND idempotency_key = :key",
                new MapSqlParameterSource("owner", owner).addValue("key", key),
                (rs, n) -> new Receipt(rs.getString("fingerprint"), rs.getString("response"))).stream().findFirst();
    }

    public void saveReceipt(UUID owner, UUID key, String fingerprint, String responseJson) {
        jdbc.update("INSERT INTO study_session_receipts (user_id, idempotency_key, fingerprint, response) "
                        + "VALUES (:owner, :key, :fingerprint, CAST(:response AS jsonb))",
                new MapSqlParameterSource("owner", owner).addValue("key", key)
                        .addValue("fingerprint", fingerprint).addValue("response", responseJson));
    }

    /** The task, only when its goal belongs to the owner. */
    public Optional<TaskRef> findOwnedTask(UUID owner, UUID taskId) {
        return jdbc.query("SELECT t.id, t.goal_id, t.title, g.subject FROM study_tasks t "
                        + "JOIN study_goals g ON g.id = t.goal_id WHERE t.id = :task AND g.user_id = :owner",
                new MapSqlParameterSource("task", taskId).addValue("owner", owner),
                (rs, n) -> new TaskRef(rs.getObject("id", UUID.class), rs.getObject("goal_id", UUID.class),
                        rs.getString("title"), rs.getString("subject"))).stream().findFirst();
    }

    public boolean goalOwned(UUID owner, UUID goalId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM study_goals WHERE id = :goal AND user_id = :owner)",
                new MapSqlParameterSource("goal", goalId).addValue("owner", owner), Boolean.class));
    }

    public Optional<Stored> findActive(UUID owner) {
        return jdbc.query("SELECT " + COLUMNS + " FROM study_sessions WHERE user_id = :owner "
                        + "AND status IN ('RUNNING', 'PAUSED')",
                new MapSqlParameterSource("owner", owner), MAPPER).stream().findFirst();
    }

    public Optional<Stored> find(UUID owner, UUID id, boolean forUpdate) {
        return jdbc.query("SELECT " + COLUMNS + " FROM study_sessions WHERE id = :id AND user_id = :owner"
                        + (forUpdate ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("id", id).addValue("owner", owner), MAPPER).stream().findFirst();
    }

    public Stored insertRunning(UUID owner, TaskRef task, Instant now) {
        return jdbc.queryForObject("INSERT INTO study_sessions (user_id, task_id, goal_id, task_title_snapshot, "
                        + "subject_snapshot, status, started_at, last_resumed_at) VALUES (:owner, :task, :goal, :title, "
                        + ":subject, 'RUNNING', :now, :now) RETURNING " + COLUMNS,
                new MapSqlParameterSource("owner", owner).addValue("task", task.taskId())
                        .addValue("goal", task.goalId()).addValue("title", task.title())
                        .addValue("subject", task.subject()).addValue("now", odt(now)), MAPPER);
    }

    /** Applies a transition guarded by the loaded version; returns the stored row. */
    public Stored update(Stored current, StudySessionPolicy.State next) {
        List<Stored> rows = jdbc.query("UPDATE study_sessions SET status = :status, "
                        + "accumulated_focus_seconds = :seconds, last_resumed_at = :resumed, ended_at = :ended, "
                        + "version = version + 1 WHERE id = :id AND user_id = :owner AND version = :version "
                        + "RETURNING " + COLUMNS,
                new MapSqlParameterSource("status", next.status().name())
                        .addValue("seconds", next.accumulatedSeconds()).addValue("resumed", odt(next.lastResumedAt()))
                        .addValue("ended", odt(next.endedAt())).addValue("id", current.id())
                        .addValue("owner", current.userId()).addValue("version", current.version()), MAPPER);
        if (rows.isEmpty()) {
            throw StudySessionException.versionConflict(current.version() + 1);
        }
        return rows.get(0);
    }

    public List<Stored> list(UUID owner, UUID taskId, int limit, long offset) {
        return jdbc.query("SELECT " + COLUMNS + " FROM study_sessions WHERE user_id = :owner"
                        + (taskId == null ? "" : " AND task_id = :task")
                        + " ORDER BY started_at DESC, id ASC LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource("owner", owner).addValue("task", taskId)
                        .addValue("limit", limit).addValue("offset", offset), MAPPER);
    }

    public long count(UUID owner, UUID taskId) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM study_sessions WHERE user_id = :owner"
                        + (taskId == null ? "" : " AND task_id = :task"),
                new MapSqlParameterSource("owner", owner).addValue("task", taskId), Long.class);
        return total == null ? 0 : total;
    }

    /** Only COMPLETED sessions count towards study time; CANCELLED ones stay in history. */
    public StudyTime studyTime(UUID owner, UUID goalId) {
        return jdbc.queryForObject("SELECT count(*) AS sessions, COALESCE(sum(accumulated_focus_seconds), 0) AS seconds "
                        + "FROM study_sessions WHERE user_id = :owner AND goal_id = :goal AND status = 'COMPLETED'",
                new MapSqlParameterSource("owner", owner).addValue("goal", goalId),
                (rs, n) -> new StudyTime(rs.getLong("sessions"), rs.getLong("seconds")));
    }
}
