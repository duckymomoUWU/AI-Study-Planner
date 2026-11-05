CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX app_users_email_unique ON app_users (lower(email));

CREATE TABLE study_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    subject VARCHAR(100) NOT NULL CHECK (length(trim(subject)) > 0),
    topics TEXT NOT NULL CHECK (length(trim(topics)) > 0),
    start_date DATE NOT NULL,
    exam_date DATE NOT NULL,
    minutes_per_day INTEGER NOT NULL CHECK (minutes_per_day BETWEEN 15 AND 480),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (exam_date > start_date AND exam_date - start_date <= 90)
);
CREATE INDEX study_goals_user_idx ON study_goals(user_id);

CREATE TABLE study_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES study_goals(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    scheduled_date DATE NOT NULL,
    estimated_minutes INTEGER NOT NULL CHECK (estimated_minutes BETWEEN 1 AND 480),
    completed BOOLEAN NOT NULL DEFAULT false,
    source VARCHAR(16) NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'ai')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX study_tasks_goal_date_idx ON study_tasks(goal_id, scheduled_date);

CREATE TABLE plan_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES study_goals(id) ON DELETE CASCADE,
    goal_version INTEGER NOT NULL,
    proposal JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at)
);
CREATE INDEX plan_drafts_goal_idx ON plan_drafts(goal_id);
-- Ownership, daily total, task deadlines and proposal shape must also be checked
-- in the service layer when implementing features. This is an initial schema only.
