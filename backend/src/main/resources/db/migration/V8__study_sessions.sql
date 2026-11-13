-- Durable Pomodoro focus sessions. Time is measured by the server (docs/team-handoff 03-API-CONTRACT, section B).
-- Task and goal links are SET NULL on delete so study history survives; names are copied at start time.
CREATE TABLE study_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    task_id UUID REFERENCES study_tasks(id) ON DELETE SET NULL,
    goal_id UUID REFERENCES study_goals(id) ON DELETE SET NULL,
    task_title_snapshot VARCHAR(200) NOT NULL,
    subject_snapshot VARCHAR(100) NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    planned_focus_seconds INTEGER NOT NULL DEFAULT 1500 CHECK (planned_focus_seconds = 1500),
    accumulated_focus_seconds INTEGER NOT NULL DEFAULT 0
        CHECK (accumulated_focus_seconds BETWEEN 0 AND 1500),
    started_at TIMESTAMPTZ NOT NULL,
    last_resumed_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    -- RUNNING always has a resume point; no other status has one.
    CHECK ((status = 'RUNNING') = (last_resumed_at IS NOT NULL)),
    -- Only finished sessions have an end time.
    CHECK ((status IN ('COMPLETED', 'CANCELLED')) = (ended_at IS NOT NULL))
);

-- At most one RUNNING or PAUSED session per account; this index, not an if-check, decides a start race.
CREATE UNIQUE INDEX study_sessions_one_active_idx ON study_sessions(user_id)
    WHERE status IN ('RUNNING', 'PAUSED');
CREATE INDEX study_sessions_history_idx ON study_sessions(user_id, started_at DESC, id);
CREATE INDEX study_sessions_task_idx ON study_sessions(task_id);
CREATE INDEX study_sessions_goal_idx ON study_sessions(goal_id) WHERE status = 'COMPLETED';

-- One receipt per owner and Idempotency-Key. The fingerprint covers the operation, target and payload.
CREATE TABLE study_session_receipts (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    idempotency_key UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, idempotency_key)
);
