#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 -m unittest discover -s deploy/tests -p 'test_recipe.py' -v
