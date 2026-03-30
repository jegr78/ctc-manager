#!/usr/bin/env bash
set -euo pipefail

SITE_DIR="docs/site"
NOW=$(date +"%Y-%m-%d %H:%M:%S")
TIMESTAMP_SLUG=$(date -j -f "%Y-%m-%d %H:%M:%S" "$NOW" +"%Y%m%d-%H%M%S")
BRANCH="site/update-${TIMESTAMP_SLUG}"
COMMIT_MSG="Site Update ${NOW}"

# Ensure we're in the repo root
cd "$(git rev-parse --show-toplevel)"

# Must be on master
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "master" ]; then
    echo "Error: Must be on master branch (currently on '${CURRENT_BRANCH}')."
    exit 1
fi

# Working tree must be clean outside docs/site
if [ -n "$(git status --porcelain --ignore-submodules | grep -v "^.. ${SITE_DIR}")" ]; then
    echo "Error: Uncommitted changes outside ${SITE_DIR}. Commit or stash first."
    exit 1
fi

# Check for changes in docs/site
CHANGES=$(git status --porcelain "$SITE_DIR")
if [ -z "$CHANGES" ]; then
    echo "No changes in ${SITE_DIR} — nothing to deploy."
    exit 0
fi

echo "Changes detected in ${SITE_DIR}:"
echo "$CHANGES"
echo ""

# Create branch, commit, push
git checkout -b "$BRANCH"
git add "$SITE_DIR"
git commit -m "$COMMIT_MSG"
git push -u origin "$BRANCH"

# Create PR and merge
PR_URL=$(gh pr create --title "$COMMIT_MSG" --body "Automated site update." --assignee jegr78)
echo "PR created: ${PR_URL}"

echo "Waiting for CI checks..."
if ! gh pr checks "$BRANCH" --watch --fail-fast; then
    echo "CI checks failed. PR remains open: ${PR_URL}"
    git switch master
    git branch -d "$BRANCH" 2>/dev/null || true
    exit 1
fi

gh pr merge "$BRANCH" --squash --delete-branch
echo "PR merged."

# Cleanup
git switch master
git pull
git branch -d "$BRANCH" 2>/dev/null || true
echo "Done. Site update deployed."
