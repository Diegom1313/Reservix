ALTER TABLE bookings ADD COLUMN payment_intent VARCHAR(255);
ALTER TABLE outbox_events ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
DROP INDEX idx_outbox_pendientes;
CREATE INDEX idx_outbox_pendientes ON outbox_events(next_attempt_at, id) WHERE published_at IS NULL;

CREATE TABLE login_failures (
  id BIGSERIAL PRIMARY KEY,
  identifier CHAR(64) NOT NULL,
  failed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_login_failures ON login_failures(identifier, failed_at);
