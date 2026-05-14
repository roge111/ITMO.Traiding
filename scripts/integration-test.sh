#!/usr/bin/env bash
# Integration test: Postgres + ClickHouse + Go ingest + gateway /quotes and /api/register.
# Run from repository root: ./scripts/integration-test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export ROOT

if docker compose version &>/dev/null 2>&1; then
  DOCKER_COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DOCKER_COMPOSE=(docker-compose)
elif [[ -x "${HOME}/.docker/cli-plugins/docker-compose" ]]; then
  DOCKER_COMPOSE=("${HOME}/.docker/cli-plugins/docker-compose")
elif [[ -x "/Applications/Docker.app/Contents/Resources/cli-plugins/docker-compose" ]]; then
  DOCKER_COMPOSE=(/Applications/Docker.app/Contents/Resources/cli-plugins/docker-compose)
elif [[ -x "/usr/local/lib/docker/cli-plugins/docker-compose" ]]; then
  DOCKER_COMPOSE=(/usr/local/lib/docker/cli-plugins/docker-compose)
else
  echo "[error] Install Docker Compose (enable in Docker Desktop, or: docker compose / docker-compose / ~/.docker/cli-plugins/docker-compose)" >&2
  exit 1
fi
dcompose() { "${DOCKER_COMPOSE[@]}" "$@"; }

export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
export POSTGRES_USER="${POSTGRES_USER:-postgres}"
export POSTGRES_JDBC_URL="${POSTGRES_JDBC_URL:-jdbc:postgresql://localhost:5432/itmo_traiding_system}"

export CLICKHOUSE_ADDR="${CLICKHOUSE_ADDR:-localhost:9000}"
export CLICKHOUSE_DATABASE="${CLICKHOUSE_DATABASE:-default}"
export CLICKHOUSE_USER="${CLICKHOUSE_USER:-default}"
export CLICKHOUSE_PASSWORD="${CLICKHOUSE_PASSWORD:-}"

INTEGRATION_USER="${INTEGRATION_USER:-integration_user}"
INTEGRATION_PASS="${INTEGRATION_PASS:-password123}"
if [[ "$(uname -s)" == Darwin ]]; then
  QUOTES_TMP="${QUOTES_TMP:-$(mktemp -t itmo-quotes)}"
else
  QUOTES_TMP="${QUOTES_TMP:-$(mktemp /tmp/itmo-quotes.XXXXXX)}"
fi
GATEWAY_PID_FILE="${TMPDIR:-/tmp}/itmo-gateway-integration.pid"
GATEWAY_LOG="${TMPDIR:-/tmp}/itmo-gateway-integration.log"

log() { printf '%s %s\n' "[$(date '+%Y-%m-%d %H:%M:%S')]" "$*"; }

cleanup_gateway() {
  if [[ -f "$GATEWAY_PID_FILE" ]]; then
    local pid
    pid="$(cat "$GATEWAY_PID_FILE" 2>/dev/null || true)"
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
    rm -f "$GATEWAY_PID_FILE"
  fi
}
trap cleanup_gateway EXIT

log "Starting docker compose (postgres, clickhouse)…"
dcompose up -d postgres clickhouse

log "Waiting for PostgreSQL…"
for i in $(seq 1 60); do
  if dcompose exec -T postgres pg_isready -U "$POSTGRES_USER" -d itmo_traiding_system &>/dev/null; then
    break
  fi
  sleep 1
  if [[ "$i" == 60 ]]; then
    log "ERROR: PostgreSQL not ready"
    exit 1
  fi
done

log "Waiting for ClickHouse HTTP…"
for i in $(seq 1 60); do
  if curl -fsS "http://localhost:8123/?query=SELECT%201" | grep -qx 1; then
    break
  fi
  sleep 1
  if [[ "$i" == 60 ]]; then
    log "ERROR: ClickHouse not ready"
    exit 1
  fi
done

log "Ensuring users table in PostgreSQL…"
dcompose exec -T postgres psql -U "$POSTGRES_USER" -d itmo_traiding_system -v ON_ERROR_STOP=1 <<'EOSQL'
CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    balance INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
DELETE FROM users WHERE username = 'integration_user';
EOSQL

log "Resetting ClickHouse quotes table…"
dcompose exec -T clickhouse clickhouse-client --query "DROP TABLE IF EXISTS quotes" || true

cat >"$QUOTES_TMP" <<'EOF'
AAPL,100,2025-01-01 10:00:00
AAPL,120,2025-01-01 10:00:01
AAPL,90,2025-01-01 10:00:02
GOOG,200,2025-01-01 10:00:03
EOF

log "Go: tidy, test (if any), build…"
(
  cd "$ROOT/server/go"
  go mod tidy
  go test ./...
  go build -o "$ROOT/server/go/itmo-ingest" .
)

log "Go: ingest quotes (one-shot)…"
export QUOTES_LOG_PATH="$QUOTES_TMP"
export PROCESS_EXISTING_AND_EXIT=true
"$ROOT/server/go/itmo-ingest"
unset PROCESS_EXISTING_AND_EXIT

log "Verifying ClickHouse FINAL state…"
dcompose exec -T clickhouse clickhouse-client --query \
  "SELECT quote_name, last_cost, min_cost, max_cost, percentage_change FROM quotes FINAL ORDER BY quote_name FORMAT TabSeparated" > /tmp/itmo-ch-verify.tsv
python3 <<'PY'
import pathlib
p = pathlib.Path("/tmp/itmo-ch-verify.tsv")
text = p.read_text().strip()
rows = [r.split("\t") for r in text.splitlines() if r.strip()]
want = [
    ("AAPL", "90", "90", "120", "-25"),
    ("GOOG", "200", "200", "200", "0"),
]
if len(rows) != 2:
    raise SystemExit(f"expected 2 rows, got {len(rows)}: {rows}")
for i, (wr, ww) in enumerate(zip(rows, want)):
    name, last, mn, mx, pct = wr
    wname, wlast, wmn, wmx, wpct = ww
    if name != wname:
        raise SystemExit(f"row {i} name {name!r} != {wname!r}")
    if last != wlast or mn != wmn or mx != wmx:
        raise SystemExit(f"row {i} costs mismatch: {wr} vs {ww}")
    pf = float(pct)
    wf = float(wpct)
    if abs(pf - wf) > 1e-3:
        raise SystemExit(f"row {i} pct {pf} != {wf}")
print("ClickHouse FINAL rows OK")
PY

log "Gateway: Gradle test and build…"
(
  cd "$ROOT/gateway"
  ./gradlew test --no-daemon
  ./gradlew build --no-daemon -x test
)

log "Gateway: start in background…"
(
  cd "$ROOT/gateway"
  nohup env POSTGRES_PASSWORD="$POSTGRES_PASSWORD" POSTGRES_USER="$POSTGRES_USER" \
    POSTGRES_JDBC_URL="$POSTGRES_JDBC_URL" \
    CLICKHOUSE_JDBC_URL="${CLICKHOUSE_JDBC_URL:-jdbc:clickhouse://localhost:8123/default}" \
    CLICKHOUSE_USER="$CLICKHOUSE_USER" CLICKHOUSE_PASSWORD="$CLICKHOUSE_PASSWORD" \
    ./gradlew run --no-daemon >"$GATEWAY_LOG" 2>&1 &
  echo $! >"$GATEWAY_PID_FILE"
)

log "Waiting for http://localhost:8080 …"
for i in $(seq 1 120); do
  if curl -fsS "http://localhost:8080/quotes" >/dev/null 2>&1; then
    break
  fi
  sleep 2
  if [[ "$i" == 120 ]]; then
    log "ERROR: gateway did not become ready (see $GATEWAY_LOG)"
    tail -n 80 "$GATEWAY_LOG" || true
    exit 1
  fi
done

log "Checking GET /quotes…"
QUOTES_HTML="$(curl -fsS "http://localhost:8080/quotes")"
echo "$QUOTES_HTML" | grep -q "AAPL" || { log "ERROR: /quotes missing AAPL"; exit 1; }
echo "$QUOTES_HTML" | grep -q "GOOG" || { log "ERROR: /quotes missing GOOG"; exit 1; }

log "Checking POST /api/register (form fields: login, password)…"
curl -fsS -X POST "http://localhost:8080/api/register" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "login=${INTEGRATION_USER}" \
  --data-urlencode "password=${INTEGRATION_PASS}" | grep -q "successfully" || {
  log "ERROR: registration response unexpected"
  exit 1
}

log "Verifying user row in PostgreSQL…"
FULL_HASH="$(dcompose exec -T postgres psql -U "$POSTGRES_USER" -d itmo_traiding_system -Atc \
  "SELECT password_hash FROM users WHERE username='${INTEGRATION_USER}'" | tr -d '\r\n')"
echo "$FULL_HASH" | grep -qE '^\$2[aby]\$' || {
  log "ERROR: password_hash does not look like bcrypt: ${FULL_HASH:-empty}"
  exit 1
}

log "Checking duplicate registration…"
DUP_BODY="$(curl -sS -X POST "http://localhost:8080/api/register" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "login=${INTEGRATION_USER}" \
  --data-urlencode "password=${INTEGRATION_PASS}" \
  -w "\n%{http_code}" )"
DUP_CODE="$(echo "$DUP_BODY" | tail -n1)"
DUP_TEXT="$(echo "$DUP_BODY" | sed '$d')"
[[ "$DUP_CODE" == "400" ]] || { log "ERROR: duplicate expected HTTP 400, got $DUP_CODE body=$DUP_TEXT"; exit 1; }
echo "$DUP_TEXT" | grep -qi "already" || { log "ERROR: duplicate body should mention already: $DUP_TEXT"; exit 1; }

log "SHOW TABLES (ClickHouse)…"
dcompose exec -T clickhouse clickhouse-client --query "SHOW TABLES"

cleanup_gateway
rm -f "$ROOT/server/go/itmo-ingest" /tmp/itmo-ch-verify.tsv "$QUOTES_TMP" 2>/dev/null || true

log "All integration checks passed."
