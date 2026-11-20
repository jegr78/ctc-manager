#!/usr/bin/env bash
# Decides which parts of release VERSION are still missing for SHA and writes publish, github_release
# and image to GITHUB_OUTPUT. Existing parts are reused only when they belong to SHA: an unpublished
# draft of an earlier master revision is deleted and an image of another revision is rebuilt.
set -euo pipefail
shopt -s inherit_errexit

: "${SHA:?}" "${VERSION:?}" "${IMAGE:?}"
OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"
out() { echo "$1" >> "${OUTPUT}"; }

if [ "${DRY_RUN:-false}" = "true" ]; then
  echo "Dry-run: release v${VERSION} would be published"
  exit 0
fi

errors=$(mktemp)
trap 'rm -f "${errors}"' EXIT
if release=$(gh release view "v${VERSION}" --json isDraft,targetCommitish \
    --jq '"\(.isDraft) \(.targetCommitish)"' 2> "${errors}"); then
  read -r draft target <<< "${release}"
  if [ "${target}" = "${SHA}" ]; then
    github_release=false
  elif [ "${draft}" = "true" ] && git merge-base --is-ancestor "${target}" "${SHA}" 2> /dev/null; then
    echo "::notice::Deleting the unpublished draft v${VERSION} of ${target}; ${SHA} contains its changes"
    gh release delete "v${VERSION}" --yes
    github_release=true
  else
    echo "::error::Release v${VERSION} targets ${target}, not ${SHA}"
    exit 1
  fi
elif grep -q 'release not found' "${errors}"; then
  github_release=true
else
  cat "${errors}" >&2
  exit 1
fi

revision=$(docker buildx imagetools inspect "${IMAGE}:${VERSION}" \
  --format '{{ index .Image.Config.Labels "org.opencontainers.image.revision" }}' 2> /dev/null) || revision=
if [ "${revision}" = "${SHA}" ]; then
  image=false
else
  image=true
fi

out "publish=true"
out "github_release=${github_release}"
out "image=${image}"
