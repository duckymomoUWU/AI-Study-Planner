-- Confirmation receipts and the draft uniqueness constraint commit atomically with the saved tasks.
CREATE TABLE plan_confirmations (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    idempotency_key UUID NOT NULL,
    draft_id UUID NOT NULL UNIQUE REFERENCES plan_drafts(id) ON DELETE CASCADE,
    fingerprint VARCHAR(64) NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, idempotency_key)
);
