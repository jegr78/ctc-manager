#!/usr/bin/env bash
set -u
# Match the opening-tag form, not the bare identifier, so this guard's own pattern literal does not self-trip.
violations=$(grep -nE '<(rerunFailingTestsCount|retryCount)' pom.xml || true)
if [ -n "$violations" ]; then
  echo "[CI-06 build-guard] FAIL: Surefire/Failsafe rerun/retry config detected in pom.xml:"
  echo "$violations"
  echo "rerunFailingTestsCount and retryCount are PERMANENTLY FORBIDDEN (CLAUDE.md No Flaky Dismissal)."
  echo "Stabilise flaky tests at the root cause - never via rerun loops or timeout bumps. See docs/ci/FLAKY-TEST-POLICY.md."
  exit 1
fi
echo "[CI-06 build-guard] OK - no rerun/retry config in pom.xml."
exit 0
