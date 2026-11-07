#!/usr/bin/env bash
set -euo pipefail
umask 077
installation=/srv/ctc-manager
DEPLOY_ROOT=$(cd "$(dirname "$0")/.." && pwd -P)
export DEPLOY_ROOT PYTHONDONTWRITEBYTECODE=1
source "$DEPLOY_ROOT/lib/common.sh"
operation=phpmyadmin-start
# Deploy, backup and restore start phpMyAdmin again while they still hold the lock.
for attempt in $(seq 960); do
    [ -e "$installation/.ctc-manager.lock" ] || break
    sleep 1
done
acquire_lock
trap release_lock EXIT
python3 "$DEPLOY_ROOT/phpmyadmin/service.py" start
release_lock
trap - EXIT
exec python3 "$DEPLOY_ROOT/phpmyadmin/service.py" wait
