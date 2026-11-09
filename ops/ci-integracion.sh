#!/usr/bin/env bash
set -euo pipefail

cat > .env <<V
DB_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -hex 32)
PAYMENTS_FAKE=true
STRIPE_WEBHOOK_SECRET=whsec_ci
V
docker compose up -d --build
timeout 180 sh -c 'until curl -fs http://127.0.0.1:8080/actuator/health >/dev/null; do sleep 3; done'
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U reservix -d reservix -tAc \
  "INSERT INTO class_sessions (title, starts_at, capacity, price_cents) VALUES ('Carga', now() + interval '3 days', 20, 1200) RETURNING id" > /tmp/session
SESSION=$(head -n 1 /tmp/session | tr -d '[:space:]')
docker run --rm --network host -v "$PWD/load:/load:ro" -e BASE_URL=http://127.0.0.1:8080 -e SESSION_ID="$SESSION" grafana/k6:0.55.0 run /load/reservas.js
ACTIVAS=$(docker compose exec -T db psql -U reservix -d reservix -tAc "SELECT COUNT(*) FROM bookings WHERE session_id = $SESSION AND status IN ('PENDING_PAYMENT','CONFIRMED')" | tr -d '[:space:]')
echo "Reservas activas: $ACTIVAS de 20"
test "$ACTIVAS" = "20"
curl -kfs https://localhost/actuator/health
