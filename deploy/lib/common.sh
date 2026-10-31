#!/usr/bin/env bash
fail() { printf 'CTC: %s\n' "$*" >&2; exit 1; }
config_value() { python3 "$DEPLOY_ROOT/lib/config.py" get "$installation" "$1"; }
verify_daemon() {
  local info
  info=$(docker --host "$docker_host" info --format '{{json .}}') || fail 'cannot reach configured Docker daemon'
  printf '%s' "$info" | python3 -c '
import json, sys
info = json.load(sys.stdin)
if "name=rootless" not in info.get("SecurityOptions", []):
 sys.exit("CTC: configured daemon must be rootless")
if info.get("MemoryLimit") is not True:
 sys.exit("CTC: daemon cannot enforce memory limits")
'
}
compose() {
  CTC_APP_IMAGE=$(config_value appImage) \
  CTC_DATABASE_IMAGE=$(config_value databaseImage) \
  CTC_APP_ENV_FILE=$(config_value appEnvFile) \
  CTC_DB_PASSWORD_FILE=$(config_value databasePasswordFile) \
  CTC_DB_ROOT_PASSWORD_FILE=$(config_value databaseRootPasswordFile) \
  CTC_GOOGLE_FILE=$(config_value googleCredentialsFile) \
  docker --host "$docker_host" compose --project-name ctc-manager \
    --project-directory "$installation" --env-file /dev/null \
    -f "$DEPLOY_ROOT/compose.yaml" "$@"
}
acquire_lock() {
  lock_directory="$installation/.ctc-manager.lock"
  mkdir "$lock_directory" 2>/dev/null || fail 'installation lock exists; reconcile its owner and journal before retrying'
  printf '%s\n' "$$" > "$lock_directory/owner"
  python3 -c 'import json,os,sys,time; json.dump({"pid":os.getpid(),"launcherPid":int(sys.argv[2]),"startedAt":time.time(),"operation":sys.argv[3]},open(sys.argv[1],"w"))' "$lock_directory/metadata.json" "$$" "$operation"
}
release_lock() {
  if [ -d "${lock_directory:-}" ] && [ "$(cat "$lock_directory/owner" 2>/dev/null)" = "$$" ]; then
    rm -f "$lock_directory/owner" "$lock_directory/metadata.json"
    rmdir "$lock_directory"
  fi
}
write_journal() {
  python3 "$DEPLOY_ROOT/lib/config.py" journal "$installation" "$operation" "$1"
}
check_port() {
  local running
  running=$(compose ps --status running --format json app)
  if [ "$running" != '[]' ] && [ -n "$running" ]; then return; fi
  python3 -c 'import socket,sys; s=socket.socket();
try: s.bind(("127.0.0.1",9092))
except OSError: sys.exit("CTC: host port 9092 is already occupied")
finally: s.close()'
}
