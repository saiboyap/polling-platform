-- Add poll type metadata to polls
ALTER TABLE polls
    ADD COLUMN poll_type  VARCHAR(20) NOT NULL DEFAULT 'SINGLE_CHOICE',
    ADD COLUMN max_choices INT        NOT NULL DEFAULT 1;

-- Relax the vote uniqueness from (poll_id, user_id) to (poll_id, user_id, option_id)
-- so that MULTI_CHOICE polls can store multiple rows per user.
ALTER TABLE votes DROP CONSTRAINT IF EXISTS uq_poll_user;
ALTER TABLE votes ADD CONSTRAINT uq_poll_user_option UNIQUE (poll_id, user_id, option_id);

-- Add a service-level uniqueness check for SINGLE_CHOICE polls in the application layer.
-- The DB only enforces (poll, user, option) uniqueness.

-- Free-text responses (used when poll_type = 'FREE_TEXT')
CREATE TABLE free_text_votes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id       UUID NOT NULL REFERENCES polls(id)  ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    response_text TEXT NOT NULL,
    voted_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_free_text_poll_user UNIQUE (poll_id, user_id)
);

CREATE INDEX idx_free_text_votes_poll ON free_text_votes(poll_id);
