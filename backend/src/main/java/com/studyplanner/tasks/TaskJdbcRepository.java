package com.studyplanner.tasks;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.studyplanner.tasks.dto.TaskResponse;

@Repository
public class TaskJdbcRepository {

    public record StoredTask(
            UUID id,
            UUID goalId,
            String goalSubject,
            String title,
            LocalDate scheduledDate,
            int estimatedMinutes,
            boolean completed,
            String source,
            Instant createdAt
    ) {
        public TaskResponse toResponse() {
            return new TaskResponse(
                    id, goalId, goalSubject, title, scheduledDate,
                    estimatedMinutes, completed, source, createdAt
            );
        }
    }

    private static final RowMapper<StoredTask> TASK_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("created_at");
        Instant createdAt = ts != null ? ts.toInstant() : Instant.now();
        String goalSubject = null;
        try {
            goalSubject = rs.getString("goal_subject");
        } catch (Exception ignored) {}

        return new StoredTask(
                rs.getObject("id", UUID.class),
                rs.getObject("goal_id", UUID.class),
                goalSubject,
                rs.getString("title"),
                rs.getObject("scheduled_date", LocalDate.class),
                rs.getInt("estimated_minutes"),
                rs.getBoolean("completed"),
                rs.getString("source"),
                createdAt
        );
    };

    private final NamedParameterJdbcTemplate jdbc;

    public TaskJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    public StoredTask insertTask(UUID goalId, String title, LocalDate scheduledDate, int estimatedMinutes, String source, String goalSubject) {
        var params = new MapSqlParameterSource()
                .addValue("goalId", goalId)
                .addValue("title", title)
                .addValue("scheduledDate", scheduledDate)
                .addValue("estimatedMinutes", estimatedMinutes)
                .addValue("source", source != null ? source : "manual");

        var rows = jdbc.query(
                """
                INSERT INTO study_tasks (goal_id, title, scheduled_date, estimated_minutes, completed, source)
                VALUES (:goalId, :title, :scheduledDate, :estimatedMinutes, false, :source)
                RETURNING id, goal_id, title, scheduled_date, estimated_minutes, completed, source, created_at
                """,
                params,
                (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    return new StoredTask(
                            rs.getObject("id", UUID.class),
                            rs.getObject("goal_id", UUID.class),
                            goalSubject,
                            rs.getString("title"),
                            rs.getObject("scheduled_date", LocalDate.class),
                            rs.getInt("estimated_minutes"),
                            rs.getBoolean("completed"),
                            rs.getString("source"),
                            ts != null ? ts.toInstant() : Instant.now()
                    );
                }
        );

        if (rows.isEmpty()) {
            throw new IllegalStateException("Failed to insert study task");
        }
        return rows.get(0);
    }

    public Optional<StoredTask> findTaskOwned(UUID userId, UUID taskId) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("taskId", taskId);

        return jdbc.query(
                """
                SELECT t.id, t.goal_id, g.subject AS goal_subject, t.title,
                       t.scheduled_date, t.estimated_minutes, t.completed,
                       t.source, t.created_at
                  FROM study_tasks t
                  JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :userId AND t.id = :taskId
                """,
                params,
                TASK_MAPPER
        ).stream().findFirst();
    }

    public Optional<StoredTask> findTaskInGoalOwned(UUID userId, UUID goalId, UUID taskId) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("goalId", goalId)
                .addValue("taskId", taskId);

        return jdbc.query(
                """
                SELECT t.id, t.goal_id, g.subject AS goal_subject, t.title,
                       t.scheduled_date, t.estimated_minutes, t.completed,
                       t.source, t.created_at
                  FROM study_tasks t
                  JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :userId AND g.id = :goalId AND t.id = :taskId
                """,
                params,
                TASK_MAPPER
        ).stream().findFirst();
    }

    public List<StoredTask> listTasksInGoal(UUID userId, UUID goalId, LocalDate date, Boolean completed, int offset, int limit) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("goalId", goalId)
                .addValue("date", date)
                .addValue("completed", completed)
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbc.query(
                """
                SELECT t.id, t.goal_id, g.subject AS goal_subject, t.title,
                       t.scheduled_date, t.estimated_minutes, t.completed,
                       t.source, t.created_at
                  FROM study_tasks t
                  JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :userId AND g.id = :goalId
                   AND (:date IS NULL OR t.scheduled_date = :date)
                   AND (:completed IS NULL OR t.completed = :completed)
                 ORDER BY t.scheduled_date ASC, t.created_at ASC, t.id ASC
                 LIMIT :limit OFFSET :offset
                """,
                params,
                TASK_MAPPER
        );
    }

    public long countTasksInGoal(UUID userId, UUID goalId, LocalDate date, Boolean completed) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("goalId", goalId)
                .addValue("date", date)
                .addValue("completed", completed);

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM study_tasks t
                  JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :userId AND g.id = :goalId
                   AND (:date IS NULL OR t.scheduled_date = :date)
                   AND (:completed IS NULL OR t.completed = :completed)
                """,
                params,
                Long.class
        );
        return count != null ? count : 0L;
    }

    public List<StoredTask> listTasksByUserAndDate(UUID userId, LocalDate date, int offset, int limit) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("date", date)
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbc.query(
                """
                SELECT t.id, t.goal_id, g.subject AS goal_subject, t.title,
                       t.scheduled_date, t.estimated_minutes, t.completed,
                       t.source, t.created_at
                  FROM study_tasks t
                  JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :userId
                   AND (:date IS NULL OR t.scheduled_date = :date)
                 ORDER BY t.scheduled_date ASC, t.created_at ASC, t.id ASC
                 LIMIT :limit OFFSET :offset
                """,
                params,
                TASK_MAPPER
        );
    }

    public long countTasksByUserAndDate(UUID userId, LocalDate date) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("date", date);

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM study_tasks t
                  JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :userId
                   AND (:date IS NULL OR t.scheduled_date = :date)
                """,
                params,
                Long.class
        );
        return count != null ? count : 0L;
    }

    public List<StoredTask> listOverdueTasks(UUID userId, LocalDate today) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("today", today);

        return jdbc.query(
                """
                SELECT t.id, t.goal_id, g.subject AS goal_subject, t.title,
                       t.scheduled_date, t.estimated_minutes, t.completed,
                       t.source, t.created_at
                  FROM study_tasks t
                  JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :userId
                   AND t.scheduled_date < :today
                   AND t.completed = false
                 ORDER BY t.scheduled_date ASC, t.created_at ASC, t.id ASC
                """,
                params,
                TASK_MAPPER
        );
    }

    public int getDailyUsedMinutes(UUID goalId, LocalDate date, UUID excludeTaskId) {
        var params = new MapSqlParameterSource()
                .addValue("goalId", goalId)
                .addValue("date", date)
                .addValue("excludeTaskId", excludeTaskId);

        Integer total = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(estimated_minutes), 0)
                  FROM study_tasks
                 WHERE goal_id = :goalId
                   AND scheduled_date = :date
                   AND (:excludeTaskId IS NULL OR id <> :excludeTaskId)
                """,
                params,
                Integer.class
        );
        return total != null ? total : 0;
    }

    public boolean updateTask(UUID taskId, UUID goalId, String title, LocalDate scheduledDate, int estimatedMinutes, boolean completed) {
        var params = new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("goalId", goalId)
                .addValue("title", title)
                .addValue("scheduledDate", scheduledDate)
                .addValue("estimatedMinutes", estimatedMinutes)
                .addValue("completed", completed);

        return jdbc.update(
                """
                UPDATE study_tasks
                   SET title = :title,
                       scheduled_date = :scheduledDate,
                       estimated_minutes = :estimatedMinutes,
                       completed = :completed
                 WHERE id = :taskId AND goal_id = :goalId
                """,
                params
        ) == 1;
    }

    public boolean deleteTask(UUID taskId, UUID goalId) {
        var params = new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("goalId", goalId);

        return jdbc.update(
                "DELETE FROM study_tasks WHERE id = :taskId AND goal_id = :goalId",
                params
        ) == 1;
    }

    public boolean incrementGoalVersion(UUID userId, UUID goalId) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("goalId", goalId);

        return jdbc.update(
                "UPDATE study_goals SET version = version + 1 WHERE id = :goalId AND user_id = :userId",
                params
        ) == 1;
    }

    public record ProgressCount(long total, long completed) {}

    public ProgressCount getGoalProgress(UUID userId, UUID goalId) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("goalId", goalId);

        return jdbc.queryForObject(
                """
                SELECT COUNT(*) AS total_tasks,
                       COUNT(*) FILTER (WHERE t.completed = true) AS completed_tasks
                  FROM study_tasks t
                  JOIN study_goals g ON g.id = t.goal_id
                 WHERE g.user_id = :userId AND g.id = :goalId
                """,
                params,
                (rs, rowNum) -> new ProgressCount(
                        rs.getLong("total_tasks"),
                        rs.getLong("completed_tasks")
                )
        );
    }
}
