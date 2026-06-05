#!/usr/bin/env bash
set -u
violations=$(grep -rE 'th:(replace|insert|include)="~\{[^"]*\(.*\$\{.*\}.*\)\}"' src/main/resources/templates/ | grep -vF 'layout(${pageTitle}' || true)
if [ -n "$violations" ]; then
  echo "[PLAT-07 build-guard] Forbidden Thymeleaf fragment-call expression detected (Plan 05):"
  echo "$violations"
  echo "Move the value to the controller via model.addAttribute(\"pageTitle\", ...) and use ~{layout :: layout(\${pageTitle}, ~{::section})}."
  echo "See .planning/phases/71-*/71-CONTEXT.md Decisions D-05 + D-12 for the canonical fix."
  exit 1
fi
echo "[PLAT-07 build-guard] OK - no Thymeleaf fragment-call expression offenders."
exit 0
