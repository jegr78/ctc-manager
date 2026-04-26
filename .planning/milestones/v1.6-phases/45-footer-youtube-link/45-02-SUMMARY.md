---
phase: 45-footer-youtube-link
plan: "02"
subsystem: sitegen-templates
tags: [tdd, green-phase, footer, youtube, static-site]
dependency_graph:
  requires: [45-01]
  provides: [LINK-05, LINK-06]
  affects: [layout.html, SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [hardcoded-external-link, footer-link-class, target-blank-rel-noopener]
key_files:
  created: []
  modified:
    - src/main/resources/templates/site/layout.html
decisions:
  - "YouTube link placed unconditionally after active season link block (D-05) — always visible regardless of activeSeasonSlug"
  - "Plain href (not th:href) used for hardcoded external URL per D-06"
  - "footer-sep separator added before YouTube link, matching existing pattern"
metrics:
  duration: "4 minutes"
  completed: "2026-04-16"
  tasks_completed: 2
  files_modified: 1
---

# Phase 45 Plan 02: Footer YouTube Link — TDD GREEN Summary

**One-liner:** YouTube link added unconditionally to site footer in layout.html, turning both RED tests GREEN with 979 tests passing.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add YouTube link to layout.html footer | 37b00a3 | layout.html |
| 2 | Full test suite verification | (no files changed) | — |

## What Was Built

Added two lines to the footer's `<div class="footer-links">` in `src/main/resources/templates/site/layout.html`, after the conditional active-season `<a>` element:

1. `<span class="footer-sep" aria-hidden="true">&middot;</span>` — unconditional separator
2. `<a href="https://www.youtube.com/@CommunityTeamCup" class="footer-link" target="_blank" rel="noopener">YouTube</a>` — unconditional YouTube link

Decisions honored:
- **D-01:** Link text is `YouTube` (plain text)
- **D-02:** CSS class is `footer-link` (no new CSS)
- **D-03:** `target="_blank" rel="noopener"` on the `<a>` tag
- **D-04:** Preceded by `<span class="footer-sep">`; placed after last existing footer link
- **D-05:** No `th:if` guard — renders on every page unconditionally
- **D-06:** Plain `href` (not `th:href`) with hardcoded URL

## GREEN Phase Verification

Both new tests now pass:
- `givenLayout_whenGenerate_thenFooterContainsYouTubeLink` (LINK-05) — PASS
- `givenSeasonPage_whenGenerate_thenFooterContainsYouTubeLink` (LINK-06) — PASS

Full suite: **979 tests, 0 failures, 0 errors** — BUILD SUCCESS, JaCoCo check passed (>= 82%).

## TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| RED | 8ef4479 (Plan 01) | PASS — two failing tests |
| GREEN | 37b00a3 | PASS — both tests now pass |
| REFACTOR | n/a | Not needed — one-line insertion, no cleanup required |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — only a hardcoded external link added; `rel="noopener"` guards against tab-napping per T-45-01 (accepted risk).

## Self-Check: PASSED

- [x] `src/main/resources/templates/site/layout.html` — modified, commit 37b00a3 exists
- [x] YouTube `<a>` element present in footer with correct href, class, target, rel, text
- [x] YouTube link and separator are outside any `th:if` block (unconditional)
- [x] All 54 SiteGeneratorServiceTest tests pass (GREEN)
- [x] Full suite 979 tests, BUILD SUCCESS, JaCoCo threshold met
