package com.studyplanner.goals;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PlanDraftRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PlanDraftRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public record StoredDraft(
            UUID id,
            UUID goalId,
            int goalVersion,
            String proposalJson,
            Instant createdAt,
            Instant expiresAt
    ) {}

    public record TaskToCreate(
            String title,
            LocalDate scheduledDate,
            int estimatedMinutes,
            String source
    ) {}

    private static final RowMapper<StoredDraft> DRAFT_MAPPER = (rs, rowNum) -> new StoredDraft(
            rs.getObject("id", UUID.class),
            rs.getObject("goal_id", UUID.class),
            rs.getInt("goal_version"),
            rs.getString("proposal"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant()
    );

    public StoredDraft insertDraft(UUID goalId, int goalVersion, String proposalJson, Instant expiresAt) {
        Objects.requireNonNull(goalId, "goalId");
        Objects.requireNonNull(proposalJson, "proposalJson");
        Objects.requireNonNull(expiresAt, "expiresAt");

        var params = new MapSqlParameterSource()
                .addValue("goalId", goalId)
                .addValue("goalVersion", goalVersion)
                .addValue("proposal", proposalJson)
                .addValue("expiresAt", java.sql.Timestamp.from(expiresAt));

        var rows = jdbc.query(
                """
                INSERT INTO plan_drafts (goal_id, goal_version, proposal, expires_at)
                VALUES (:goalId, :goalVersion, :proposal::jsonb, :expiresAt)
                RETURNING id, goal_id, goal_version, proposal::text AS proposal, created_at, expires_at
                """,
                params, DRAFT_MAPPER
        );

        if (rows.isEmpty()) {
            throw new IllegalStateException("Failed to insert plan_draft");
        }
        return rows.getFirst();
    }

    public Optional<StoredDraft> findDraft(UUID trustedOwnerId, UUID goalId, UUID draftId) {
        Objects.requireNonNull(trustedOwnerId, "trustedOwnerId");
        Objects.requireNonNull(goalId, "goalId");
        Objects.requireNonNull(draftId, "draftId");

        var params = new MapSqlParameterSource()
                .addValue("ownerId", trustedOwnerId)
                .addValue("goalId", goalId)
                .addValue("draftId", draftId);

        return jdbc.query(
                """
                SELECT d.id, d.goal_id, d.goal_version, d.proposal::text AS proposal, d.created_at, d.expires_at
                  FROM plan_drafts d
                  JOIN study_goals g ON g.id = d.goal_id
                 WHERE g.user_id = :ownerId AND d.goal_id = :goalId AND d.id = :draftId
                """,
                params, DRAFT_MAPPER
        ).stream().findFirst();
    }

    public Optional<StoredDraft> findLatestDraft(UUID trustedOwnerId, UUID goalId) {
        Objects.requireNonNull(trustedOwnerId, "trustedOwnerId");
        Objects.requireNonNull(goalId, "goalId");

        var params = new MapSqlParameterSource()
                .addValue("ownerId", trustedOwnerId)
                .addValue("goalId", goalId);

        return jdbc.query(
                """
                SELECT d.id, d.goal_id, d.goal_version, d.proposal::text AS proposal, d.created_at, d.expires_at
                  FROM plan_drafts d
                  JOIN study_goals g ON g.id = d.goal_id
                 WHERE g.user_id = :ownerId AND d.goal_id = :goalId
                 ORDER BY d.created_at DESC
                 LIMIT 1
                """,
                params, DRAFT_MAPPER
        ).stream().findFirst();
    }

    @Transactional
    public List<UUID> confirmDraft(UUID trustedOwnerId, UUID goalId, UUID draftId, List<TaskToCreate> tasks) {
        Objects.requireNonNull(trustedOwnerId, "trustedOwnerId");
        Objects.requireNonNull(goalId, "goalId");
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(tasks, "tasks");

        // 1. Verify ownership and draft existence
        var draft = findDraft(trustedOwnerId, goalId, draftId)
                .orElseThrow(() -> new GoalService.GoalNotFoundException());

        // 2. Increment goal version
        int updated = jdbc.update(
                """
                UPDATE study_goals
                   SET version = version + 1
                 WHERE user_id = :ownerId AND id = :goalId
                """,
                new MapSqlParameterSource()
                        .addValue("ownerId", trustedOwnerId)
                        .addValue("goalId", goalId)
        );
        if (updated != 1) {
            throw new GoalService.GoalNotFoundException();
        }

        // 3. Insert tasks into study_tasks
        List<UUID> createdIds = new ArrayList<>();
        for (TaskToCreate task : tasks) {
            var params = new MapSqlParameterSource()
                    .addValue("goalId", goalId)
                    .addValue("title", task.title())
                    .addValue("scheduledDate", task.scheduledDate())
                    .addValue("estimatedMinutes", task.estimatedMinutes())
                    .addValue("source", task.source() != null ? task.source() : "ai");

            var taskRows = jdbc.query(
                    """
                    INSERT INTO study_tasks (goal_id, title, scheduled_date, estimated_minutes, source)
                    VALUES (:goalId, :title, :scheduledDate, :estimatedMinutes, :source)
                    RETURNING id
                    """,
                    params,
                    (rs, rowNum) -> rs.getObject("id", UUID.class)
            );
            if (!taskRows.isEmpty()) {
                createdIds.add(taskRows.getFirst());
            }
        }

        // 4. Clean up draft once confirmed
        jdbc.update(
                "DELETE FROM plan_drafts WHERE id = :draftId",
                new MapSqlParameterSource("draftId", draftId)
        );

        return createdIds;
    }
}
