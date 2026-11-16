#!/usr/bin/env bash
# Downloads the pinned car cutout model and verifies its SHA-256.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
target="${1:-$root/data/models/cutout.onnx}"
url="$(sed -n 's/^url=//p' "$root/config/cutout-model.properties")"
sha="$(sed -n 's/^sha256=//p' "$root/config/cutout-model.properties")"
if [[ -f "$target" ]] && echo "$sha  $target" | shasum -a 256 -c - >/dev/null 2>&1; then
  echo "Cutout model already present: $target"
  exit 0
fi
mkdir -p "$(dirname "$target")"
tmp="$target.part"
curl -fsSL --max-time 900 -o "$tmp" "$url"
echo "$sha  $tmp" | shasum -a 256 -c - >/dev/null
mv "$tmp" "$target"
echo "Cutout model installed: $target"
