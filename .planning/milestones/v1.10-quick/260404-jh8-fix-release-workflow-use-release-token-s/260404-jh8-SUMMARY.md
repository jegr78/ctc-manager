---
phase: quick
plan: 260404-jh8
subsystem: ci
tags: [release, github-actions, authentication]
key-files:
  modified: [.github/workflows/release.yml]
decisions:
  - "Keep GITHUB_TOKEN for gh CLI and Docker login — only checkout needs PAT for push bypass"
metrics:
  duration: "32s"
  completed: "2026-04-04"
  tasks: 1
  files: 1
---

# Quick Task 260404-jh8: Fix Release Workflow — Use RELEASE_TOKEN Summary

**One-liner:** Release workflow checkout uses RELEASE_TOKEN PAT to bypass branch protection for SNAPSHOT version bump push.

## What Changed

The release workflow's checkout step was updated to use `secrets.RELEASE_TOKEN` instead of `secrets.GITHUB_TOKEN`. When `actions/checkout` uses a PAT, it embeds the token in the git remote URL, allowing all subsequent `git push` operations to authenticate with that PAT. This is necessary because the default `GITHUB_TOKEN` cannot push directly to a branch-protected `master`.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Update release workflow to use RELEASE_TOKEN | 5b338ce | .github/workflows/release.yml |

## Deviations from Plan

None -- plan executed exactly as written.

## Verification

- `RELEASE_TOKEN` appears exactly 1 time (checkout step, line 23)
- `GITHUB_TOKEN` appears exactly 2 times (gh CLI line 117, Docker login line 131)
- YAML indentation is correct
- Comment explains why PAT is needed

## Known Stubs

None.
