CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE polls (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question    TEXT         NOT NULL,
    created_by  UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE poll_options (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID         NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_text VARCHAR(500) NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID      NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    user_id     UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    option_id   UUID      NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
    voted_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_poll_user UNIQUE (poll_id, user_id)
);

CREATE INDEX idx_polls_created_by  ON polls(created_by);
CREATE INDEX idx_polls_status      ON polls(status);
CREATE INDEX idx_poll_options_poll ON poll_options(poll_id);
CREATE INDEX idx_votes_poll        ON votes(poll_id);
CREATE INDEX idx_votes_user        ON votes(user_id);
CREATE INDEX idx_votes_option      ON votes(option_id);
