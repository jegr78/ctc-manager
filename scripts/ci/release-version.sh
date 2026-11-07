#!/usr/bin/env bash
# Determines the release version of SHA from the conventional commits since the last tag and writes
# should_skip, resume, new_version, last_tag and bump to GITHUB_OUTPUT. A SHA that already carries
# a release tag resumes that version.
set -euo pipefail
shopt -s inherit_errexit

: "${SHA:?}"
OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"
out() { echo "$1" >> "${OUTPUT}"; }

tagged=$(git tag --points-at "${SHA}" --list 'v[0-9]*.[0-9]*.[0-9]*' | awk '/^v[0-9]+\.[0-9]+\.[0-9]+$/' | sort -V | tail -1)
if [ -n "${tagged}" ]; then
  out "resume=true"
  out "new_version=${tagged#v}"
  echo "Resuming release ${tagged#v}"
  exit 0
fi
out "resume=false"

last_tag=$(git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*' \
  | awk '/^v[0-9]+\.[0-9]+\.[0-9]+$/ && !found { print; found = 1 }')
if [ -z "${last_tag}" ]; then
  pom_version=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
  version=${pom_version%-SNAPSHOT}
  echo "Initial release: ${version} (from pom.xml ${pom_version})"
else
  out "last_tag=${last_tag}"
  subjects=$(git log "${last_tag}..${SHA}" --pretty=format:'%s')
  bodies=$(git log "${last_tag}..${SHA}" --pretty=format:'%B')
  bump=
  if printf '%s\n' "${subjects}" | grep -qE '^[a-z]+(\(.+\))?!:' \
     || printf '%s\n' "${bodies}" | grep -qE '^BREAKING[ -]CHANGE:'; then
    bump=major
  elif printf '%s\n' "${subjects}" | grep -qE '^feat(\(.+\))?:'; then
    bump=minor
  elif printf '%s\n' "${subjects}" | grep -qE '^(fix|perf)(\(.+\))?:|^chore\(deps\):'; then
    bump=patch
  fi
  if [ -z "${bump}" ]; then
    out "should_skip=true"
    echo "No releasable commits since ${last_tag}, skipping release"
    exit 0
  fi
  IFS='.' read -r major minor patch <<< "${last_tag#v}"
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
