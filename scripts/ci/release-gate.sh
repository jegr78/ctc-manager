#!/usr/bin/env bash
# Decides whether the checked revision SHA may be released and writes decision=release|skip.
# Release needs every required check of SHA to have succeeded, and SHA to be the tip of
# origin/master apart from release and snapshot commits of an earlier attempt. A check that is
# still running, failed or was cancelled, and a revision that master has moved past, are skips;
# a later run releases the newer revision. Any API or git failure fails the script.
set -euo pipefail
shopt -s inherit_errexit

: "${OWNER_REPO:?}" "${SHA:?}" "${REQUIRED_CHECKS:?}"
OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"

decide() {
  echo "decision=$1" >> "${OUTPUT}"
  if [ "$1" = "skip" ]; then
    echo "::notice::Release skipped for ${SHA}: $2"
  else
    echo "Releasing ${SHA}: $2"
  fi
  exit 0
}

git cat-file -e "${SHA}^{commit}"
tip=$(git rev-parse origin/master)
if ! git merge-base --is-ancestor "${SHA}" "${tip}"; then
  decide skip "it is not on master"
fi
foreign=$(git log --format=%s "${SHA}..${tip}" \
  | grep -vE '^release: v[0-9]+\.[0-9]+\.[0-9]+$|^chore: bump version to [0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT \[skip ci\]$' || true)
if [ -n "${foreign}" ]; then
  decide skip "master has moved on to ${tip}"
fi

pages=$(gh api "repos/${OWNER_REPO}/commits/${SHA}/check-runs" -f per_page=100 --paginate --slurp) || exit 1
states=$(printf '%s' "${pages}" | REQUIRED_CHECKS="${REQUIRED_CHECKS}" perl -MJSON::PP -0777 -ne '
  my %latest;
  for my $page (@{ JSON::PP->new->decode($_) }) {
    for my $run (@{ $page->{check_runs} }) {
      my $current = $latest{ $run->{name} };
      $latest{ $run->{name} } = $run if !$current || $run->{id} > $current->{id};
    }
  }
  for my $name (split /\n/, $ENV{REQUIRED_CHECKS}) {
    next if $name eq "";
    my $run = $latest{$name};
    my $state = !$run ? "missing"
      : $run->{status} ne "completed" ? "pending"
      : ($run->{conclusion} // "") eq "success" ? "success"
      : $run->{conclusion};
    print "$name=$state\n";
  }')

not_ready=$(printf '%s\n' "${states}" | grep -v '=success$' || true)
if [ -n "${not_ready}" ]; then
  decide skip "required checks not successful: $(printf '%s' "${not_ready}" | paste -sd ' ')"
fi
decide release "all required checks succeeded"
