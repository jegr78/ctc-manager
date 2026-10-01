#!/usr/bin/env bash
# Decides whether the checked revision SHA may be released and writes decision=release|skip.
# REQUIRED_JOBS lists "<workflow path>:<job name>" lines. Release needs, for each listed workflow,
# its latest push run on SHA to have succeeded with every listed job, and SHA to be the tip of
# origin/master apart from release and snapshot commits of an earlier attempt. Workflow runs are
# read instead of check runs because any workflow token with checks: write can forge a check run.
# A check that is still running, failed, was cancelled or is missing, and a revision that master
# has moved past, are skips; a later run releases the newer revision. Any API or git failure
# fails the script.
set -euo pipefail
shopt -s inherit_errexit

: "${OWNER_REPO:?}" "${SHA:?}" "${REQUIRED_JOBS:?}"
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
subjects=$(git log --format=%s "${SHA}..${tip}")
foreign=$(printf '%s\n' "${subjects}" \
  | grep -vE '^$|^release: v[0-9]+\.[0-9]+\.[0-9]+$|^chore: bump version to [0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT \[skip ci\]$' || true)
if [ -n "${foreign}" ]; then
  decide skip "master has moved on to ${tip}"
fi

runs=$(gh api --method GET "repos/${OWNER_REPO}/actions/runs" -f head_sha="${SHA}" -f event=push \
  -f branch=master -f per_page=100 --paginate --slurp) || exit 1
latest=$(printf '%s' "${runs}" | REQUIRED_JOBS="${REQUIRED_JOBS}" perl -MJSON::PP -0777 -ne '
  my %latest;
  for my $page (@{ JSON::PP->new->decode($_) }) {
    for my $run (@{ $page->{workflow_runs} }) {
      my $current = $latest{ $run->{path} };
      $latest{ $run->{path} } = $run if !$current || $run->{id} > $current->{id};
    }
  }
  my %seen;
  for my $line (split /\n/, $ENV{REQUIRED_JOBS}) {
    next if $line eq "";
    my ($path) = split /:/, $line, 2;
    next if $seen{$path}++;
    my $run = $latest{$path};
    my $state = !$run ? "missing"
      : $run->{status} ne "completed" ? "pending"
      : ($run->{conclusion} // "") eq "success" ? "success"
      : $run->{conclusion};
    print "$path=", ($run ? $run->{id} : 0), "=$state\n";
  }')

states=""
while IFS='=' read -r path run_id state; do
  [ -n "${path}" ] || continue
  if [ "${state}" != "success" ]; then
    states+="${path}=${state}"$'\n'
    continue
  fi
  jobs=$(gh api --method GET "repos/${OWNER_REPO}/actions/runs/${run_id}/jobs" -f filter=latest -f per_page=100 \
    --paginate --slurp) || exit 1
  states+=$(printf '%s' "${jobs}" | WORKFLOW="${path}" REQUIRED_JOBS="${REQUIRED_JOBS}" perl -MJSON::PP -0777 -ne '
    my %jobs;
    for my $page (@{ JSON::PP->new->decode($_) }) {
      $jobs{ $_->{name} } = $_ for @{ $page->{jobs} };
    }
    for my $line (split /\n/, $ENV{REQUIRED_JOBS}) {
      my ($path, $name) = split /:/, $line, 2;
      next if !defined $name || $path ne $ENV{WORKFLOW};
      my $job = $jobs{$name};
      my $state = !$job ? "missing"
        : $job->{status} ne "completed" ? "pending"
        : ($job->{conclusion} // "") eq "success" ? "success"
        : $job->{conclusion};
      print "$name=$state\n";
    }')$'\n'
done <<< "${latest}"

not_ready=$(printf '%s' "${states}" | grep -vE '^$|=success$' || true)
if [ -n "${not_ready}" ]; then
  decide skip "required checks not successful: $(printf '%s' "${not_ready}" | paste -sd ' ')"
fi
decide release "all required checks succeeded"
