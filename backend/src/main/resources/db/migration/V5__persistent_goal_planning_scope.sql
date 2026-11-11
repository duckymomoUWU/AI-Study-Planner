ALTER TABLE study_goals ADD COLUMN planning_scope JSONB NOT NULL DEFAULT '{}'::jsonb;

-- Recover the latest source-backed settings that existed before this migration.
-- Keep only genuine overrides; default minutes remain governed by the goal.
WITH latest AS (
    SELECT DISTINCT ON (goal_id) goal_id, proposal->'scope' AS scope
    FROM plan_drafts
    WHERE jsonb_typeof(proposal->'scope'->'selectedTopics') = 'array'
    ORDER BY goal_id, created_at DESC, id DESC
)
UPDATE study_goals g SET planning_scope = jsonb_build_object(
    'selectedTopics', l.scope->'selectedTopics',
    'dailyMinutes', COALESCE((
        SELECT jsonb_object_agg(e.key,e.value)
        FROM jsonb_each(COALESCE(l.scope->'dailyMinutes','{}'::jsonb)) e
        WHERE e.key >= g.start_date::text AND e.key < g.exam_date::text
          AND jsonb_typeof(e.value)='number' AND (e.value::text)::numeric BETWEEN 0 AND g.minutes_per_day
          AND (e.value::text)::numeric <> g.minutes_per_day
    ),'{}'::jsonb)
) FROM latest l WHERE l.goal_id=g.id;
