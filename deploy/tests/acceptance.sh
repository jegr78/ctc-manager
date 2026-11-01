#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
for check in recipe.sh release-bundle.sh host-setup.sh recovery.sh update.sh maintenance.sh migration.sh site-publication.sh; do
  bash "deploy/tests/$check"
done
