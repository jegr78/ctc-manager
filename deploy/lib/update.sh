#!/usr/bin/env bash
run_update() {
  python3 "$DEPLOY_ROOT/lib/update.py" --directory "$installation" --deploy-root "$DEPLOY_ROOT" "$operation" "$@"
}
