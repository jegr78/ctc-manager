#!/usr/bin/env bash
run_maintenance() {
  python3 "$DEPLOY_ROOT/lib/maintenance.py" --directory "$installation" --deploy-root "$DEPLOY_ROOT" "$operation" "$@"
}
