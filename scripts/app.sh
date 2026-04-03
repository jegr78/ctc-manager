#!/usr/bin/env bash
# Start/stop the CTC Manager application.
# Usage:
#   ./scripts/app.sh start [profile]   — start with given profile (default: dev)
#   ./scripts/app.sh stop              — stop the running instance
#   ./scripts/app.sh status            — check if running
#
# Profiles: dev, dev,demo, local

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$PROJECT_DIR/target/app.pid"

start() {
  local profile="${1:-dev}"

  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "App is already running (PID $(cat "$PID_FILE"))."
    exit 1
  fi

  # Load base .env if present
  if [ -f "$PROJECT_DIR/.env" ]; then
    set -a; source "$PROJECT_DIR/.env"; set +a
  fi

  # Load profile-specific .env (overrides base)
  local primary_profile="${profile%%,*}"
  if [ -f "$PROJECT_DIR/.env.$primary_profile" ]; then
    set -a; source "$PROJECT_DIR/.env.$primary_profile"; set +a
  fi

  echo "Starting CTC Manager with profile '$profile'..."
  cd "$PROJECT_DIR"
  ./mvnw spring-boot:run -Dspring-boot.run.profiles="$profile" &
  local pid=$!
  mkdir -p "$(dirname "$PID_FILE")"
  echo "$pid" > "$PID_FILE"
  echo "Started with PID $pid (profile: $profile)."
}

stop() {
  if [ ! -f "$PID_FILE" ]; then
    echo "No PID file found. App not running?"
    exit 1
  fi

  local pid
  pid=$(cat "$PID_FILE")

  if kill -0 "$pid" 2>/dev/null; then
    echo "Stopping CTC Manager (PID $pid)..."
    kill "$pid"
    # Wait up to 10 seconds for graceful shutdown
    for i in $(seq 1 10); do
      if ! kill -0 "$pid" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    if kill -0 "$pid" 2>/dev/null; then
      echo "Forcing shutdown..."
      kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
    echo "Stopped."
  else
    echo "Process $pid not running. Cleaning up PID file."
    rm -f "$PID_FILE"
  fi
}

status() {
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "App is running (PID $(cat "$PID_FILE"))."
  else
    echo "App is not running."
    [ -f "$PID_FILE" ] && rm -f "$PID_FILE"
  fi
}

case "${1:-}" in
  start)  start "${2:-dev}" ;;
  stop)   stop ;;
  status) status ;;
  *)
    echo "Usage: $0 {start|stop|status} [profile]"
    echo "  start [profile]  — start app (default: dev)"
    echo "  stop             — stop running app"
    echo "  status           — check if running"
    echo ""
    echo "Profiles: dev, dev,demo, local"
    exit 1
    ;;
esac
