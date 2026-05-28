#!/usr/bin/env bash
# Start/stop/status of the CTC Manager application — per-profile.
# Usage:
#   ./scripts/app.sh start [profile]    — start with given profile (default: dev)
#   ./scripts/app.sh stop  [profile]    — stop the instance for that profile (default: dev)
#   ./scripts/app.sh stop  --all        — stop every running profile instance
#   ./scripts/app.sh status [profile]   — check if that profile is running (default: dev)
#   ./scripts/app.sh status --all       — list all running profile instances
#
# Profiles: dev, dev,demo, local
#
# Per-profile PID file: data/app-{primaryProfile}.pid (data/ is .gitignore'd).
# Profile-to-port map: dev=9090, local=9091, docker=8080 (lsof fallback).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_DIR="$PROJECT_DIR/data"

primary_profile() {
  echo "${1%%,*}"
}

pid_file_for() {
  echo "$PID_DIR/app-$(primary_profile "$1").pid"
}

port_for() {
  case "$(primary_profile "$1")" in
    dev)    echo "9090" ;;
    local)  echo "9091" ;;
    docker) echo "8080" ;;
    *)      echo "9090" ;;  # safe default
  esac
}

start() {
  local profile="${1:-dev}"
  local pid_file
  pid_file="$(pid_file_for "$profile")"

  if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "Profile '$profile' is already running (PID $(cat "$pid_file"))."
    exit 1
  fi

  # Load base .env if present
  if [ -f "$PROJECT_DIR/.env" ]; then
    set -a; source "$PROJECT_DIR/.env"; set +a
  fi

  # Load profile-specific .env (overrides base)
  local primary
  primary="$(primary_profile "$profile")"
  if [ -f "$PROJECT_DIR/.env.$primary" ]; then
    set -a; source "$PROJECT_DIR/.env.$primary"; set +a
  fi

  echo "Starting CTC Manager with profile '$profile' (port $(port_for "$profile"))..."
  cd "$PROJECT_DIR"
  ./mvnw spring-boot:run -Dspring-boot.run.profiles="$profile" &
  local pid=$!
  mkdir -p "$PID_DIR"
  echo "$pid" > "$pid_file"
  echo "Started with PID $pid (profile: $profile, pid-file: $pid_file)."
}

# Stop a single profile instance. Returns 0 if anything was killed, 1 otherwise.
stop_one() {
  local profile="$1"
  local pid_file
  pid_file="$(pid_file_for "$profile")"
  local port
  port="$(port_for "$profile")"
  local pid=""
  local killed=0

  if [ -f "$pid_file" ]; then
    pid=$(cat "$pid_file")
    if kill -0 "$pid" 2>/dev/null; then
      echo "Stopping CTC Manager (profile $profile, PID $pid)..."
      kill "$pid"
      for i in $(seq 1 10); do
        if ! kill -0 "$pid" 2>/dev/null; then
          break
        fi
        sleep 1
      done
      if kill -0 "$pid" 2>/dev/null; then
        echo "Forcing shutdown (profile $profile)..."
        kill -9 "$pid" 2>/dev/null || true
      fi
      killed=1
    fi
    rm -f "$pid_file"
  fi

  # lsof fallback: in case the PID file was wiped (e.g. ./mvnw clean) the port-bound
  # JVM survives the script-tracked Maven PID — find and kill it via port.
  if command -v lsof &> /dev/null; then
    local port_pid
    port_pid=$(lsof -t -i ":$port" 2>/dev/null || true)
    if [ -n "$port_pid" ] && { [ "$pid" != "$port_pid" ] || [ -z "$pid" ]; }; then
      echo "Found orphan process on port $port (PID $port_pid). Killing..."
      kill "$port_pid" 2>/dev/null || true
      sleep 1
      if kill -0 "$port_pid" 2>/dev/null; then
        kill -9 "$port_pid" 2>/dev/null || true
      fi
      killed=1
    fi
  elif command -v fuser &> /dev/null; then
    local port_pid
    port_pid=$(fuser "$port/tcp" 2>/dev/null | awk '{print $1}' || true)
    if [ -n "$port_pid" ] && { [ "$pid" != "$port_pid" ] || [ -z "$pid" ]; }; then
      echo "Found orphan process on port $port (PID $port_pid). Killing..."
      fuser -k "$port/tcp" 2>/dev/null || true
      killed=1
    fi
  fi

  return $((1 - killed))
}

stop() {
  local arg="${1:-dev}"

  if [ "$arg" = "--all" ]; then
    local any_killed=0
    for pid_file in "$PID_DIR"/app-*.pid; do
      [ -e "$pid_file" ] || continue
      local p
      p=$(basename "$pid_file" .pid)
      p=${p#app-}
      if stop_one "$p"; then any_killed=1; fi
    done
    if [ $any_killed -eq 1 ]; then
      echo "Stopped (all)."
    else
      echo "No running app found."
    fi
    return
  fi

  if stop_one "$arg"; then
    echo "Stopped (profile $arg)."
  else
    echo "No running app found for profile '$arg'."
  fi
}

status_one() {
  local profile="$1"
  local pid_file
  pid_file="$(pid_file_for "$profile")"
  local port
  port="$(port_for "$profile")"
  if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "  $profile : running (PID $(cat "$pid_file"), port $port)"
    return 0
  fi
  [ -f "$pid_file" ] && rm -f "$pid_file"
  if command -v lsof &> /dev/null; then
    local port_pid
    port_pid=$(lsof -t -i ":$port" -sTCP:LISTEN 2>/dev/null || true)
    if [ -n "$port_pid" ]; then
      echo "  $profile : ORPHAN on port $port (PID $port_pid, no pid-file) — run 'app.sh stop $profile' to clean up"
      return 0
    fi
  fi
  echo "  $profile : not running"
  return 1
}

status() {
  local arg="${1:-dev}"
  if [ "$arg" = "--all" ]; then
    local found=0
    for pid_file in "$PID_DIR"/app-*.pid; do
      [ -e "$pid_file" ] || continue
      local p
      p=$(basename "$pid_file" .pid)
      p=${p#app-}
      status_one "$p" >/dev/null && found=1
      status_one "$p"
    done
    if [ $found -eq 0 ]; then
      echo "No running app instances."
    fi
    return
  fi
  status_one "$arg"
}

case "${1:-}" in
  start)  start "${2:-dev}" ;;
  stop)   stop "${2:-dev}" ;;
  status) status "${2:-dev}" ;;
  *)
    echo "Usage: $0 {start|stop|status} [profile|--all]"
    echo "  start [profile]    — start app (default: dev)"
    echo "  stop  [profile]    — stop app of that profile (default: dev)"
    echo "  stop  --all        — stop every running profile instance"
    echo "  status [profile]   — check that profile (default: dev)"
    echo "  status --all       — list every running profile instance"
    echo ""
    echo "Profiles: dev, dev,demo, local"
    echo "Ports:    dev=9090, local=9091, docker=8080"
    exit 1
    ;;
esac
