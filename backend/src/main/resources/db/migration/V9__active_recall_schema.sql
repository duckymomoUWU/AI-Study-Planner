CREATE TABLE recall_materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    goal_id UUID NOT NULL REFERENCES study_goals(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL CHECK (length(trim(title)) > 0),
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('TEXT', 'PDF')),
    file_name VARCHAR(255),
    page_count INTEGER,
    character_count INTEGER NOT NULL CHECK (character_count > 0 AND character_count <= 30000),
    extracted_text TEXT NOT NULL,
    pages_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX recall_materials_user_goal_idx ON recall_materials(user_id, goal_id, created_at DESC);

CREATE TABLE recall_decks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    goal_id UUID NOT NULL REFERENCES study_goals(id) ON DELETE CASCADE,
    material_id UUID NOT NULL REFERENCES recall_materials(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL CHECK (length(trim(title)) > 0),
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE')),
    card_count INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX recall_decks_user_goal_idx ON recall_decks(user_id, goal_id, created_at DESC);

CREATE TABLE recall_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deck_id UUID NOT NULL REFERENCES recall_decks(id) ON DELETE CASCADE,
    question VARCHAR(300) NOT NULL CHECK (length(trim(question)) > 0),
    answer VARCHAR(2000) NOT NULL CHECK (length(trim(answer)) > 0),
    source_material_id UUID NOT NULL REFERENCES recall_materials(id) ON DELETE CASCADE,
    source_page INTEGER,
    source_quote VARCHAR(500) NOT NULL CHECK (length(trim(source_quote)) > 0),
    edited_by_user BOOLEAN NOT NULL DEFAULT false,
    version INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    last_rating VARCHAR(20) CHECK (last_rating IS NULL OR last_rating IN ('REMEMBERED', 'FORGOT')),
    last_reviewed_at TIMESTAMPTZ,
    review_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX recall_cards_deck_idx ON recall_cards(deck_id, sort_order ASC);

CREATE TABLE recall_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    card_id UUID NOT NULL REFERENCES recall_cards(id) ON DELETE CASCADE,
    rating VARCHAR(20) NOT NULL CHECK (rating IN ('REMEMBERED', 'FORGOT')),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX recall_reviews_card_idx ON recall_reviews(card_id, reviewed_at DESC);
CREATE INDEX recall_reviews_user_idx ON recall_reviews(user_id, reviewed_at DESC);

CREATE TABLE recall_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    request_key UUID NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT recall_receipts_user_key_action_unique UNIQUE (user_id, request_key, action_type)
);
CREATE INDEX recall_receipts_user_key_idx ON recall_receipts(user_id, request_key);
