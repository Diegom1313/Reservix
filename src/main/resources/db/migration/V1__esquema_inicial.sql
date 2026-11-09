CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(10) NOT NULL CHECK (role IN ('CUSTOMER','ADMIN')),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE class_sessions (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(160) NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL,
  capacity INTEGER NOT NULL CHECK (capacity > 0),
  price_cents INTEGER NOT NULL CHECK (price_cents > 0)
);
CREATE INDEX idx_sessions_starts ON class_sessions(starts_at);

CREATE TABLE bookings (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES class_sessions(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING_PAYMENT','CONFIRMED','CANCELLED','EXPIRED')),
  checkout_id VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL,
  hold_expires_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_booking_activa ON bookings(session_id, user_id) WHERE status IN ('PENDING_PAYMENT','CONFIRMED');
CREATE INDEX idx_bookings_expira ON bookings(status, hold_expires_at);

CREATE TABLE processed_webhooks (
  event_id VARCHAR(255) PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_events (
  id BIGSERIAL PRIMARY KEY,
  type VARCHAR(40) NOT NULL,
  booking_id BIGINT NOT NULL REFERENCES bookings(id),
  created_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pendientes ON outbox_events(id) WHERE published_at IS NULL;
