#!/usr/bin/env bash
# Aggregate ContextCacheKeyFingerprintListener output from target/test-perf/
# and report the top-N cache-key clusters by fragmentation score
# (occurrence x cluster-size).
#
# Usage:
#   scripts/test-perf/aggregate-fingerprints.sh [marker-dir] [top-n]
#
# Arguments:
#   marker-dir  Directory with context-loads-*-fingerprints.txt sidecars
#               (default: target/test-perf)
#   top-n       Number of clusters to report (default: 5)

set -euo pipefail

MARKER_DIR="${1:-target/test-perf}"
TOP_N="${2:-5}"

if [[ ! -d "$MARKER_DIR" ]]; then
  echo "no fingerprint data at $MARKER_DIR" >&2
  exit 1
fi

shopt -s nullglob
FILES=("$MARKER_DIR"/context-loads-*-fingerprints.txt)
shopt -u nullglob

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "no fingerprint sidecar files in $MARKER_DIR" >&2
  exit 1
fi

echo "# Top $TOP_N cache-key clusters by occurrence x cluster-size"
echo "# Source: ${#FILES[@]} sidecar file(s) in $MARKER_DIR"
echo

cat "${FILES[@]}" | awk -F'\t' -v top="$TOP_N" '
  NF >= 2 {
    hex = $1
    display = $2
    occ[hex]++
    key = hex SUBSEP display
    if (!(key in seen)) {
      seen[key] = 1
      cluster[hex]++
      sample[hex] = display
    }
  }
  END {
    n = 0
    for (h in occ) {
      n++
      hexes[n] = h
      scores[n] = occ[h] * cluster[h]
    }
    for (i = 1; i <= n; i++) {
      for (j = i + 1; j <= n; j++) {
        if (scores[j] > scores[i]) {
          ts = scores[i]; scores[i] = scores[j]; scores[j] = ts
          th = hexes[i];  hexes[i]  = hexes[j];  hexes[j]  = th
        }
      }
    }
    limit = (n < top) ? n : top
    for (i = 1; i <= limit; i++) {
      h = hexes[i]
      s = sample[h]
      if (length(s) > 80) {
        s = substr(s, 1, 80)
      }
      printf "%d. %s -- %d occurrences across %d classes (score=%d)\n", \
             i, h, occ[h], cluster[h], scores[i]
      printf "   %s\n", s
    }
  }
'
