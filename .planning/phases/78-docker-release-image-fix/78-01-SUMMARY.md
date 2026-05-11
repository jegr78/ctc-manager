---
phase: 78-docker-release-image-fix
plan: 01
subsystem: infra
tags: [docker, dockerfile, eclipse-temurin, playwright, noble, ubuntu, base-image, pin]

# Dependency graph
requires: []
provides:
  - "Dockerfile with both stages pinned to eclipse-temurin :25-jdk-noble (build) and :25-jre-noble (runtime)"
  - "Inline rationale comments above each FROM line citing Phase 78 + Playwright 1.59.0 / Ubuntu 26.04 (Plucky) incompatibility"
affects: [78-02, 78-03, release.yml, ci.yml]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Suffix-only base-image pin (no SHA256 digest) per locked D-01"
    - "Inline 'why this is pinned' comment above each FROM line per locked D-04"

key-files:
  created: []
  modified:
    - "Dockerfile"

key-decisions:
  - "D-01 honored: suffix-only pin to -noble (no @sha256 digest)"
  - "D-04 honored: one rationale comment per FROM line, both reference Phase 78 and Playwright 1.59.0 vs Ubuntu 26.04 (Plucky)"

patterns-established:
  - "Base-image pin pattern: '# Pinned to -noble: <reason>. See Phase XX / <context path>.' immediately above each FROM line"

requirements-completed: [PLAT-CI-01]

# Metrics
duration: 1min
completed: 2026-05-11
---

# Phase 78 Plan 01: Pin Dockerfile Stages to eclipse-temurin -noble Summary

**Both Dockerfile stages pinned to eclipse-temurin :25-jdk-noble (build) and :25-jre-noble (runtime), with inline Phase 78 rationale comments above each FROM line — root-cause fix for release run 25609204039 (Playwright 1.59.0 incompatibility with the silently-rotated Ubuntu 26.04 / Plucky base).**

## Performance

- **Duration:** ~1 min
- **Started:** 2026-05-11T13:48:25Z
- **Completed:** 2026-05-11T13:49:26Z
- **Tasks:** 1 / 1
- **Files modified:** 1 (Dockerfile)

## Accomplishments
- Stage 1 (JDK build) pinned: `eclipse-temurin:25-jdk` → `eclipse-temurin:25-jdk-noble`
- Stage 2 (JRE runtime) pinned: `eclipse-temurin:25-jre` → `eclipse-temurin:25-jre-noble`
- Two inline comments added (one per stage) citing Phase 78, Playwright 1.59.0, and Ubuntu 26.04 (Plucky) — points future maintainers at the incident before they "simplify" the tag
- Preserved-line audit confirmed: `libasound2t64` apt-get line, `groupadd -r ctc` non-root user setup, `PLAYWRIGHT_BROWSERS_PATH` env + `playwright install chromium` bootstrap, and `ENTRYPOINT` all untouched

## Task Commits

Each task was committed atomically:

1. **Task 1: Pin both Dockerfile FROM lines to -noble and add per-stage rationale comments** — `ffa5303` (fix)

**Plan metadata commit:** appended after this SUMMARY is written.

## Files Created/Modified
- `Dockerfile` — Pinned both `FROM eclipse-temurin:...` lines to the `-noble` suffix and added a one-line rationale comment immediately above each FROM, citing Phase 78 and the Playwright 1.59.0 / Ubuntu 26.04 incompatibility

## Decisions Made
- Followed locked decisions D-01 (suffix-only pin, no SHA256 digest) and D-04 (one inline rationale comment per FROM line) verbatim. No new decisions taken during execution.

## Deviations from Plan

None — plan executed exactly as written. The diff is the minimum 4 changed lines specified in the plan (2 FROM-line edits + 2 inserted comment lines). All acceptance criteria passed on first run.

## Issues Encountered

None. The plan was self-contained, the file was small (49 lines pre-edit, 51 lines post-edit), and both Edit operations matched on unique strings on the first try.

## Acceptance Check Results

Run after the edit, before the commit:

| Check | Expected | Actual | Pass |
|-------|----------|--------|------|
| `grep -c '^FROM eclipse-temurin:25-jdk-noble AS build$' Dockerfile` | 1 | 1 | yes |
| `grep -c '^FROM eclipse-temurin:25-jre-noble$' Dockerfile` | 1 | 1 | yes |
| `grep -cE '^FROM eclipse-temurin:25-(jdk\|jre)( \|$)' Dockerfile` (unpinned tags remaining) | 0 | 0 | yes |
| `grep -c 'Pinned to -noble' Dockerfile` | 2 | 2 | yes |
| `grep -c 'libasound2t64' Dockerfile` (Noble package preserved) | 1 | 1 | yes |
| `grep -c 'Phase 78' Dockerfile` | >= 2 | 2 | yes |
| `grep -c 'Playwright 1.59.0' Dockerfile` | >= 2 | 2 | yes |
| `grep -c 'Ubuntu 26.04' Dockerfile` | >= 2 | 2 | yes |
| `grep -c 'groupadd -r ctc' Dockerfile` (non-root user preserved) | 1 | 1 | yes |
| `grep -ci 'playwright' Dockerfile` (Playwright bootstrap preserved) | >= 2 | 7 | yes |

Post-commit deletion check: no files deleted.

## User Setup Required

None — this plan only edits a build-time Dockerfile. No environment variables, dashboard configuration, or secrets are introduced.

## Next Phase Readiness

- **Plan 78-02** can proceed: it adds the CI `docker build .` job and the build-guard step in `.github/workflows/ci.yml`. The pin established here is what the guard will enforce (`-noble` suffix on every `FROM eclipse-temurin:` line).
- **Plan 78-03** can proceed: it handles `.planning/REQUIREMENTS.md` / `.planning/ROADMAP.md` updates and the post-merge release verification.
- End-to-end verification of the pin (criterion 2 of the phase — `docker build .` succeeds locally with `playwright install chromium` passing) is deferred to Plan 78-03 per the plan's `<verification>` section, so this plan stays minimal and the diff is reviewable in isolation.
- Criterion 3 (release workflow green on master after merge) remains a post-merge verification — explicitly OUT of scope of this plan.

## Threat Surface Scan

No new threat surface introduced. Per the plan's `<threat_model>`:
- T-78-01 (supply-chain tag rotation) is **mitigated structurally** by this pin.
- T-78-02 (future human regression) is **mitigated editorially** by the inline comments naming Phase 78; structural mitigation lives in Plan 78-02 (build-guard).
- T-78-03 (info disclosure) is **accepted** — no new secrets enter the image.
- T-78-04 (release pipeline DoS by upstream drift) is **mitigated** — this is the root-cause fix.

## Self-Check: PASSED

- Files: `Dockerfile` exists, contains both pinned FROM lines and both rationale comments (verified by greps above).
- Commit: `ffa5303` exists in `git log` on branch `gsd/v1.10-platform-and-backup`.

---
*Phase: 78-docker-release-image-fix*
*Completed: 2026-05-11*
