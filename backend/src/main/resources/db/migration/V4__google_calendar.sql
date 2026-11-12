CREATE TABLE calendar_connections (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    encrypted_tokens TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE calendar_preferences (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    settings TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE calendar_previews (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    payload TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX calendar_previews_owner_idx ON calendar_previews(user_id);
CREATE TABLE calendar_exports (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    task_id UUID NOT NULL,
    calendar_id TEXT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    event_url TEXT NOT NULL,
    task_snapshot TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','CONFIRMED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, task_id),
    UNIQUE (user_id, calendar_id, event_id),
    CHECK (ends_at > starts_at)
);
-- Export receipts intentionally survive task deletion: remote events are not deleted by this MVP.
