#!/usr/bin/env bash
# Project hook: block `gh pr create` when a milestone PR already exists on the current branch.
#
# Rationale: v1.x milestone branches (`gsd/v<X>-...`) carry one long-lived PR per milestone
# (e.g., PR #122 for v1.11). The PR body is maintained as a rolling summary via `gh pr edit`,
# not by creating a new PR per phase. See memory: feedback_pr_description_update.
#
# Exit codes:
#   0 — allow the tool call (no PR exists, or environment can't determine)
#   2 — block the tool call (PR exists; message goes to Claude via stderr)

set -u

branch=$(git branch --show-current 2>/dev/null || true)
if [ -z "$branch" ]; then
    exit 0
fi

if ! command -v gh >/dev/null 2>&1; then
    exit 0
fi

existing=$(gh pr list --head "$branch" --state open --json number,url 2>/dev/null || true)
if [ -z "$existing" ] || [ "$existing" = "[]" ]; then
    exit 0
fi

num=$(printf '%s' "$existing" | grep -oE '"number":[0-9]+' | head -1 | grep -oE '[0-9]+')
url=$(printf '%s' "$existing" | grep -oE '"url":"[^"]+"' | head -1 | sed 's/"url":"//;s/"$//')

cat >&2 <<EOF
BLOCKED: PR #${num} already exists for branch '${branch}': ${url}

Use: gh pr edit ${num} --body "..." to update the rolling milestone PR.
See memory: feedback_pr_description_update — milestone branches carry one long-lived PR
maintained as a rolling summary, NOT a new PR per phase.

To view the current PR body:  gh pr view ${num} --json body --jq .body
EOF
exit 2
