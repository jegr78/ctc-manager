#!/usr/bin/env bash
set -u
REPORT=target/spotbugsXml.xml
if [ ! -f "$REPORT" ]; then
  echo "findsecbugs-loaded-guard SKIP - $REPORT not present (SpotBugs did not run)."
  exit 0
fi
if ! grep -q "com.h3xstream.findsecbugs" "$REPORT"; then
  echo "findsecbugs-loaded-guard FAIL: the find-sec-bugs detector pack was not loaded by SpotBugs."
  echo "The security patterns are silently inactive - the build stays green while nothing is scanned."
  echo "SpotBugs 4.9.8.x has this bug (spotbugs/spotbugs#4191); keep spotbugs-maven-plugin >= 4.10.1."
  echo "See CLAUDE.md 'Static Analysis (SpotBugs + find-sec-bugs)'."
  exit 1
fi
echo "findsecbugs-loaded-guard OK - find-sec-bugs detector pack active."
exit 0
