#!/usr/bin/env bash
# Determines the release version of SHA and writes should_skip, resume, new_version,
# next_snapshot, last_tag and bump to GITHUB_OUTPUT. When an earlier attempt already pushed the
# release commit and tag on top of SHA, it resumes that version instead of computing a new one.
set -euo pipefail
shopt -s inherit_errexit

: "${SHA:?}"
OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"
out() { echo "$1" >> "${OUTPUT}"; }

snapshot_after() {
  local major minor patch
  IFS='.' read -r major minor patch <<< "$1"
  echo "${major}.$((minor + 1)).0-SNAPSHOT"
}

resumed=$(git log --format='%H %P %s' "${SHA}..origin/master" \
  | awk -v sha="${SHA}" '$2 == sha && $3 == "release:" && $4 ~ /^v[0-9]+\.[0-9]+\.[0-9]+$/ { print $1 " " substr($4, 2) }')
if [ -n "${resumed}" ]; then
  read -r release_commit version <<< "${resumed}"
  if [ "$(git rev-parse "v${version}^{commit}" 2>/dev/null || true)" = "${release_commit}" ]; then
    out "resume=true"
    out "new_version=${version}"
    out "next_snapshot=$(snapshot_after "${version}")"
    echo "Resuming release ${version} from an earlier attempt"
    exit 0
  fi
fi
out "resume=false"

last_tag=$(git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*' | head -1)
if [ -z "${last_tag}" ]; then
  pom_version=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
  version=${pom_version%-SNAPSHOT}
  echo "Initial release: ${version} (from pom.xml ${pom_version})"
else
  out "last_tag=${last_tag}"
  subjects=$(git log "${last_tag}..${SHA}" --pretty=format:'%s')
  bodies=$(git log "${last_tag}..${SHA}" --pretty=format:'%B')
  if ! printf '%s\n' "${subjects}" | grep -qE '^(feat|fix|docs|refactor|perf|test|style|chore)(\(.+\))?[!]?:'; then
    out "should_skip=true"
    echo "No releasable commits since ${last_tag}, skipping release"
    exit 0
  fi
  bump=patch
  if printf '%s\n' "${subjects}" | grep -qE '^(feat|fix)(\(.+\))?!:' \
     || printf '%s\n' "${bodies}" | grep -qE '^BREAKING[ -]CHANGE:'; then
    bump=major
  elif printf '%s\n' "${subjects}" | grep -qE '^feat(\(.+\))?:'; then
    bump=minor
  fi
  IFS='.' read -r major minor patch <<< "${last_tag#v}"
  patch="${patch:-0}"
  if ! [[ "${major}" =~ ^[0-9]+$ && "${minor}" =~ ^[0-9]+$ && "${patch}" =~ ^[0-9]+$ ]]; then
    echo "::error::Invalid SemVer in last tag '${last_tag}'"
    exit 1
  fi
  case ${bump} in
    major) major=$((major + 1)); minor=0; patch=0 ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    patch) patch=$((patch + 1)) ;;
  esac
  version="${major}.${minor}.${patch}"
  out "bump=${bump}"
  echo "Release: ${version} (${bump} bump from ${last_tag})"
fi
if git rev-parse "v${version}^{}" > /dev/null 2>&1; then
  echo "::error::Tag v${version} already exists but is not a release of ${SHA}"
  exit 1
fi
out "new_version=${version}"
out "next_snapshot=$(snapshot_after "${version}")"
