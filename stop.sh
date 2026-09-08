#!/usr/bin/env bash
# stop.sh — gracefully stop the processes started by ./start.sh.
# Finds each process via its pid file (logs/hify-*.pid), sends SIGTERM to the
# whole process tree (children first), waits up to GRACE_SECONDS, then SIGKILLs
# whatever is still alive. Compatible with bash 3.2 (macOS default).
#
# Usage: ./stop.sh   (override the grace period: GRACE_SECONDS=5 ./stop.sh)

set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="${PID_DIR:-$ROOT/logs}"
GRACE_SECONDS="${GRACE_SECONDS:-10}"

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'
info() { echo -e "${GREEN}ok${NC}   $1"; }
warn() { echo -e "${YELLOW}..${NC}   $1"; }

# Collect the full descendant tree of $1 (parents first) into the global $TREE
TREE=()
collect_tree() {
  local pid="$1" child
  for child in $(pgrep -P "$pid" 2>/dev/null); do
    TREE+=("$child")
    collect_tree "$child"
  done
}

# Gracefully stop one pid file's process tree: SIGTERM -> wait -> SIGKILL
stop_process() { # pid_file display_name
  local pf="$1" name="$2"
  if [ ! -f "$pf" ]; then
    warn "$name: no pid file ($pf), nothing to stop"
    return 0
  fi
  local pid
  pid="$(cat "$pf" 2>/dev/null || true)"
  rm -f "$pf"

  if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
    warn "$name: process (pid ${pid:-unknown}) not running, removing stale pid file"
    return 0
  fi

  # Collect the process tree, then TERM children first, parent last
  TREE=("$pid")
  collect_tree "$pid"
  local i
  echo "  $name (pid $pid, ${#TREE[@]} process(es)): sending SIGTERM ..."
  for ((i = ${#TREE[@]} - 1; i >= 0; i--)); do
    kill -TERM "${TREE[i]}" 2>/dev/null || true
  done

  # Wait until the whole tree is gone or the grace period elapses
  local waited=0 alive=1 p
  while [ "$waited" -lt "$GRACE_SECONDS" ]; do
    alive=0
    for p in "${TREE[@]}"; do
      if kill -0 "$p" 2>/dev/null; then alive=1; break; fi
    done
    [ "$alive" = 1 ] || break
    sleep 1
    waited=$((waited + 1))
  done

  if [ "$alive" = 1 ]; then
    echo "  $name: still alive after ${GRACE_SECONDS}s, sending SIGKILL ..."
    for ((i = ${#TREE[@]} - 1; i >= 0; i--)); do
      kill -KILL "${TREE[i]}" 2>/dev/null || true
    done
    sleep 1
  fi
  info "$name stopped"
}

echo "==> Stopping Hify processes"

# Frontend first (frees the dev-server port), then the backend
stop_process "$PID_DIR/hify-frontend.pid" "frontend"
stop_process "$PID_DIR/hify-backend.pid" "backend"

echo ""
echo "Done. Note: processes started before pid files existed (e.g. manual"
echo "java -jar / npm run dev) are not tracked here — stop them by hand."
