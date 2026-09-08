#!/usr/bin/env bash
# start.sh — one-shot local dev environment for Hify:
#   [1] toolchain check -> [2] MySQL/Redis availability -> [3] build backend
#   -> [4] launch backend, poll /health until UP -> [5] launch Vite frontend.
# Both processes run in the background and record their pids under logs/
# (hify-backend.pid, hify-frontend.pid); stop them with ./stop.sh.
#
# Requirements: JDK 21+, Maven, Node/npm. Override via env vars:
#   MYSQL_HOST MYSQL_PORT REDIS_HOST REDIS_PORT BACKEND_PORT HEALTH_TIMEOUT

set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# ---------------------------------------------------------------- config
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-90}" # seconds to wait for backend health
LOG_DIR="${LOG_DIR:-logs}"
BACKEND_LOG="${LOG_DIR}/hify-backend.log"
FRONTEND_LOG="${LOG_DIR}/hify-frontend.log"
BACKEND_PID_FILE="${LOG_DIR}/hify-backend.pid"
FRONTEND_PID_FILE="${LOG_DIR}/hify-frontend.pid"
HEALTH_URL="http://localhost:${BACKEND_PORT}/api/v1/health"

# ---------------------------------------------------------------- helpers
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'
ok()  { echo -e "${GREEN}ok${NC}   $1"; }
die() { echo -e "${RED}fail${NC} $1" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"; }

check_tcp() { # host port name
  if nc -z -w 3 "$1" "$2" >/dev/null 2>&1; then
    ok "$3 is reachable ($1:$2)"
  else
    die "$3 is NOT reachable at $1:$2 — start it first, e.g.:\n" \
        "  docker run -d --name hify-$3 -p $2:$2 $3:latest"
  fi
}

port_in_use() { nc -z -w 2 localhost "$1" 2>/dev/null; }

# ------------------------------------------------------------- lifecycle
# On abnormal exit (die / Ctrl+C / kill) stop whatever was started;
# a normal run sets KEEP_ALIVE=1 so the processes stay up for ./stop.sh.
cleanup() {
  if [ "${KEEP_ALIVE:-0}" != "1" ]; then
    "$ROOT/stop.sh" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# ------------------------------------------------------------- [1/5] tools
echo "==> [1/5] Checking toolchain"
for c in java mvn nc curl npm; do need "$c"; done
[ -f pom.xml ]                || die "run ./start.sh from the project root (where pom.xml lives)"
[ -f hify-web/package.json ]  || die "hify-web/package.json not found"
ok "toolchain OK"

# ------------------------------------------------------------- [2/5] infra
echo "==> [2/5] Checking infrastructure"
check_tcp "$MYSQL_HOST" "$MYSQL_PORT" "MySQL"
check_tcp "$REDIS_HOST" "$REDIS_PORT" "Redis"

if port_in_use "$BACKEND_PORT"; then
  die "port $BACKEND_PORT is already in use — run ./stop.sh first, or set BACKEND_PORT"
fi

# ------------------------------------------------------------- [3/5] build
echo "==> [3/5] Building backend"
mvn -q -B -DskipTests package || die "backend build failed — check the Maven output above"
BACKEND_JAR="$(ls -t hify-app/target/hify-app-*.jar 2>/dev/null | head -1)"
[ -n "$BACKEND_JAR" ] || die "backend jar not found after build"
ok "build finished: $BACKEND_JAR"

# ------------------------------------------------------------- [4/5] run
echo "==> [4/5] Starting backend and waiting for health check"
mkdir -p "$LOG_DIR"
java -jar "$BACKEND_JAR" > "$BACKEND_LOG" 2>&1 &
echo "$!" > "$BACKEND_PID_FILE"
ok "backend started (pid $(cat "$BACKEND_PID_FILE")), log: $BACKEND_LOG"

HEALTH_PASSED=0
for _ in $(seq 1 $((HEALTH_TIMEOUT / 3))); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$HEALTH_URL" 2>/dev/null || echo 000)
  if [ "$code" = "200" ]; then
    HEALTH_PASSED=1
    ok "health check passed: GET $HEALTH_URL -> 200"
    break
  fi
  sleep 3
done

if [ "$HEALTH_PASSED" -ne 1 ]; then
  echo "" >&2
  echo "--- last lines of $BACKEND_LOG ---" >&2
  tail -30 "$BACKEND_LOG" >&2
  die "backend did not become healthy within ${HEALTH_TIMEOUT}s — see log above (MySQL/Redis credentials in hify-app/src/main/resources/application.yml?)"
fi

# ------------------------------------------------------------- [5/5] front
echo "==> [5/5] Starting frontend"
if port_in_use "$FRONTEND_PORT"; then
  die "port $FRONTEND_PORT is already in use — run ./stop.sh first, or set FRONTEND_PORT"
fi

cd hify-web || die "cannot enter hify-web"
npm install --no-audit --no-fund >/dev/null 2>&1 || die "npm install failed"
npm run dev > "$ROOT/$FRONTEND_LOG" 2>&1 &
echo "$!" > "$ROOT/$FRONTEND_PID_FILE"

# The Vite dev server usually boots in ~1s; poll briefly before declaring success
FRONTEND_OK=0
for _ in 1 2 3 4 5; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "http://localhost:${FRONTEND_PORT}/" 2>/dev/null || echo 000)
  if [ "$code" = "200" ]; then
    FRONTEND_OK=1
    break
  fi
  sleep 1
done
[ "$FRONTEND_OK" = 1 ] || die "frontend did not answer on port $FRONTEND_PORT — see $FRONTEND_LOG"

cd "$ROOT"
KEEP_ALIVE=1
echo ""
echo "Hify is up and running:"
echo "  frontend  http://localhost:${FRONTEND_PORT}   (pid $(cat "$FRONTEND_PID_FILE"))"
echo "  backend   http://localhost:${BACKEND_PORT}   (pid $(cat "$BACKEND_PID_FILE"))"
echo "  backend log   $BACKEND_LOG"
echo "  frontend log  $FRONTEND_LOG"
echo "Stop everything with:  ./stop.sh"
