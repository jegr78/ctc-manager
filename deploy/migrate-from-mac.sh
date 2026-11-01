#!/usr/bin/env bash
set -euo pipefail
umask 077
DEPLOY_ROOT=$(cd "$(dirname "$0")" && pwd -P)
operation=${1:-}
case "$operation" in
  inventory|export)
    exec python3 "$DEPLOY_ROOT/lib/migration.py" "$@"
    ;;
  rehearse|cutover)
    [ "${2:-}" = --directory ] && [ "$#" -ge 3 ] || { printf '%s\n' 'Expected OPERATION --directory PATH first.' >&2; exit 1; }
    installation=$3
    source "$DEPLOY_ROOT/lib/common.sh"
    python3 "$DEPLOY_ROOT/lib/config.py" validate "$installation"
    docker_host=$(config_value dockerHost)
    verify_daemon
    acquire_lock
    trap release_lock EXIT
    trap 'write_journal interrupted; trap - EXIT; exit 143' TERM
    trap 'write_journal interrupted; trap - EXIT; exit 130' INT
    write_journal started
    if python3 "$DEPLOY_ROOT/lib/migration.py" "$@"; then
      write_journal completed
    else
      result=$?
      if [ "$result" -ge 128 ]; then write_journal interrupted; trap - EXIT; else write_journal failed; fi
      exit "$result"
    fi
    ;;
  *) printf '%s\n' 'Expected inventory, export, rehearse or cutover.' >&2; exit 1 ;;
esac
