#!/usr/bin/env bash
set -u
# Presence patterns use [[:space:]]* between tag and value so the guard's own literals do not self-satisfy the grep.
fail=0
grep -qE '<failOnViolation>[[:space:]]*true' pom.xml || { echo "checkstyle-gate-guard FAIL: <failOnViolation>true</failOnViolation> missing or weakened in the maven-checkstyle-plugin config."; fail=1; }
grep -qE '<includeTestSourceDirectory>[[:space:]]*true' pom.xml || { echo "checkstyle-gate-guard FAIL: <includeTestSourceDirectory>true</includeTestSourceDirectory> missing - src/test/java would silently stop being scanned for unused imports."; fail=1; }
grep -q 'UnusedImports' config/checkstyle.xml || { echo "checkstyle-gate-guard FAIL: UnusedImports module missing from config/checkstyle.xml."; fail=1; }
grep -q 'RedundantImport' config/checkstyle.xml || { echo "checkstyle-gate-guard FAIL: RedundantImport module missing from config/checkstyle.xml."; fail=1; }
if [ "$fail" -ne 0 ]; then
  echo "Restore the unused-import gate config. See CLAUDE.md 'Checkstyle (Unused-Import Gate)'."
  exit 1
fi
echo "checkstyle-gate-guard OK - unused-import gate config intact."
exit 0
