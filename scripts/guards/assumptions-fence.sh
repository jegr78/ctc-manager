#!/usr/bin/env bash
set -u
violations=$(grep -rE '^import\s+(static\s+)?org\.junit\.jupiter\.api\.Assumptions(\.|;)' src/test/java/ | grep -v 'src/test/java/org/ctc/build/' || true)
if [ -n "$violations" ]; then
  echo "[CLEAN-01 build-guard] Forbidden JUnit-Jupiter Assumptions import detected:"
  echo "$violations"
  echo "Remove the JUnit Assumptions usage; either rewrite as an unconditional assertion or use AssertJ org.assertj.core.api.Assumptions.assumeThat (different package, intentional)."
  echo "See .planning/phases/92-carry-forwards-cleanup/92-CONTEXT.md Decision D-04 for the canonical fix and rationale."
  exit 1
fi
echo "[CLEAN-01 build-guard] OK - no JUnit-Jupiter Assumptions offenders."
exit 0
