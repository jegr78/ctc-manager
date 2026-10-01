#!/usr/bin/env bash
# Fails when the scanned revision has open HIGH/CRITICAL CodeQL alerts that its base does not have.
# Pull requests compare alert numbers of the PR with those of the base branch; pushes count the
# alerts first seen since ANALYSIS_STARTED_AT. Any API failure fails the gate.
set -euo pipefail

: "${OWNER_REPO:?}" "${EVENT_NAME:?}"

ALERT_LINE='.[] | select(.dismissed_at == null and ((.rule.security_severity_level // "") | IN("high", "critical")))
  | "\(.number)|\(.rule.id)|\(.most_recent_instance.location.path)|\(.created_at)"'

fetch_alerts() {
  gh api -X GET "repos/${OWNER_REPO}/code-scanning/alerts" -f state=open "$@" --paginate --jq "${ALERT_LINE}"
}

if [ "${EVENT_NAME}" = "pull_request" ]; then
  : "${PR_NUMBER:?}" "${BASE_REF:?}"
  head_alerts=$(fetch_alerts -f "pr=${PR_NUMBER}")
  base_alerts=$(fetch_alerts -f "ref=refs/heads/${BASE_REF}")
  if [ -z "${base_alerts}" ] && [ -n "${head_alerts}" ]; then
    echo "::warning::Base ${BASE_REF} has no open HIGH/CRITICAL alerts; every PR alert counts as new."
  fi
  new_alerts=$(awk -F'|' 'NR == FNR { if ($1 != "") base[$1] = 1; next } $1 != "" && !($1 in base)' \
    <(printf '%s\n' "${base_alerts}") <(printf '%s\n' "${head_alerts}"))
else
  : "${HEAD_REF:?}" "${ANALYSIS_STARTED_AT:?}"
  ref_alerts=$(fetch_alerts -f "ref=refs/heads/${HEAD_REF}")
  new_alerts=$(printf '%s\n' "${ref_alerts}" | awk -F'|' -v since="${ANALYSIS_STARTED_AT}" '$1 != "" && $4 >= since')
fi

if [ -n "${new_alerts}" ]; then
  echo "::error::New HIGH/CRITICAL CodeQL alerts introduced (number|rule|path|created):"
  printf '%s\n' "${new_alerts}"
  exit 1
fi
echo "No new HIGH/CRITICAL CodeQL alerts."
