#!/usr/bin/env bash
# Serves the generated static site from target/site on a local HTTP server.
# Usage: ./scripts/serve-site.sh [port]
#   port  — defaults to 9092

set -euo pipefail

PORT="${1:-9092}"
SITE_DIR="$(cd "$(dirname "$0")/.." && pwd)/target/site"

if ! command -v python3 &>/dev/null; then
  echo "Error: python3 is not installed."
  exit 1
fi

if [ ! -d "$SITE_DIR" ]; then
  echo "Error: $SITE_DIR does not exist."
  echo "Generate the site first via the Admin UI (POST /admin/generate)."
  exit 1
fi

echo "Serving $SITE_DIR on http://localhost:$PORT"
echo "Press Ctrl+C to stop."
python3 -m http.server "$PORT" --directory "$SITE_DIR"
