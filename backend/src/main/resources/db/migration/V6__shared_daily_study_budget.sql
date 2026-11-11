-- No guessed availability: existing accounts configure their budget explicitly.
ALTER TABLE app_users
    ADD COLUMN study_daily_minutes INTEGER CHECK (study_daily_minutes BETWEEN 0 AND 1440),
    ADD COLUMN study_daily_overrides JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN study_budget_version INTEGER NOT NULL DEFAULT 0;
