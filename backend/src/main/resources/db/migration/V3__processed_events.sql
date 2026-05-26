-- Idempotency table: tracks every Kafka event that has been successfully processed.
-- Consumers check this before acting to guarantee exactly-once semantics
-- even if Kafka delivers the same message more than once.
CREATE TABLE processed_events (
    id            VARCHAR(255) PRIMARY KEY,   -- eventId from the event envelope
    event_type    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    payload       TEXT                        -- original JSON for audit / replay
);

CREATE INDEX idx_processed_events_type ON processed_events(event_type);
CREATE INDEX idx_processed_events_at   ON processed_events(processed_at);

-- DLQ monitoring counters (one row per source topic, updated via upsert)
CREATE TABLE dlq_metrics (
    topic        VARCHAR(255) PRIMARY KEY,
    failure_count BIGINT      NOT NULL DEFAULT 0,
    last_failed_at TIMESTAMP
);
