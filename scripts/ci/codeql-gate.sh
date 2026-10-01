#!/usr/bin/env bash
# Fails when the scanned revision has open HIGH/CRITICAL CodeQL alerts that its base does not have.
#   codeql-gate.sh snapshot FILE  records the open alerts of HEAD_REF before a push is analysed
#   codeql-gate.sh                pull requests: alert numbers of the PR not open on BASE_REF;
#                                 pushes: alert numbers not in BASELINE_FILE
# Any API failure fails the gate.
set -euo pipefail
shopt -s inherit_errexit

: "${OWNER_REPO:?}" "${EVENT_NAME:?}"

# One line per open, undismissed HIGH/CRITICAL alert: number|rule|path, the path JSON-encoded and last.
alert_lines() {
  perl -MJSON::PP -0777 -ne '
    my $json = JSON::PP->new->allow_nonref;
    for my $page (@{ $json->decode($_) }) {
      for my $alert (@$page) {
        next if defined $alert->{dismissed_at};
        my $severity = $alert->{rule}{security_severity_level} // "";
        next unless $severity eq "high" || $severity eq "critical";
        print join("|", $alert->{number}, $alert->{rule}{id}, $json->encode($alert->{most_recent_instance}{location}{path} // "")), "\n";
      }
    }'
}

fetch_alerts() {
  local pages
  pages=$(gh api -X GET "repos/${OWNER_REPO}/code-scanning/alerts" -f state=open -f per_page=100 "$@" --paginate --slurp) \
    || return 1
  printf '%s' "${pages}" | alert_lines
}

new_numbers() {
  awk -F'|' 'NR == FNR { if ($1 != "") base[$1] = 1; next } $1 != "" && !($1 in base)' \
    <(printf '%s\n' "$1") <(printf '%s\n' "$2")
}

if [ "${1:-}" = "snapshot" ]; then
  : "${HEAD_REF:?}" "${2:?snapshot file}"
  fetch_alerts -f "ref=refs/heads/${HEAD_REF}" > "$2"
  exit 0
fi

if [ "${EVENT_NAME}" = "pull_request" ]; then
  : "${PR_NUMBER:?}" "${BASE_REF:?}"
  head_alerts=$(fetch_alerts -f "pr=${PR_NUMBER}")
  base_alerts=$(fetch_alerts -f "ref=refs/heads/${BASE_REF}")
else
  : "${HEAD_REF:?}" "${BASELINE_FILE:?}"
  base_alerts=$(cat "${BASELINE_FILE}")
  head_alerts=$(fetch_alerts -f "ref=refs/heads/${HEAD_REF}")
fi
new_alerts=$(new_numbers "${base_alerts}" "${head_alerts}")

if [ -n "${new_alerts}" ]; then
  echo "::error::New HIGH/CRITICAL CodeQL alerts introduced (number|rule|path):"
  printf '%s\n' "${new_alerts}"
  exit 1
fi
echo "No new HIGH/CRITICAL CodeQL alerts."
