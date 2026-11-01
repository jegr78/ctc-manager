#!/usr/bin/env bash
run_recovery() {
  python3 "$DEPLOY_ROOT/lib/recovery.py" --directory "$installation" --deploy-root "$DEPLOY_ROOT" "$operation" "$@"
}
