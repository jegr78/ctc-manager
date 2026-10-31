#!/usr/bin/env bash
set -euo pipefail
[ "$#" -eq 2 ] || { echo 'usage: publish-deployment VERSION DIRECTORY' >&2; exit 1; }
version=$1
directory=$2
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?$ ]] || { echo 'invalid release version' >&2; exit 1; }
staging=$(mktemp -d)
trap 'rm -rf "$staging"' EXIT
assets=$(gh release view "v$version" --json assets)
for suffix in .tar.gz .tar.gz.sha256; do
  name="ctc-manager-deployment-${version}${suffix}"
  [ -f "$directory/$name" ] || { echo 'missing deployment asset' >&2; exit 1; }
  if printf '%s' "$assets" | python3 -c 'import json,sys; sys.exit(0 if any(a["name"]==sys.argv[1] for a in json.load(sys.stdin)["assets"]) else 1)' "$name"; then
    gh release download "v$version" --pattern "$name" --dir "$staging"
    cmp -s "$directory/$name" "$staging/$name" || { echo 'existing deployment asset differs; refusing replacement' >&2; exit 1; }
  fi
done
for suffix in .tar.gz .tar.gz.sha256; do
  name="ctc-manager-deployment-${version}${suffix}"
  if [ ! -f "$staging/$name" ]; then
    gh release upload "v$version" "$directory/$name"
  fi
done
