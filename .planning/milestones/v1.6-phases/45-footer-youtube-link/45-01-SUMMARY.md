---
phase: 45-footer-youtube-link
plan: "01"
subsystem: sitegen-tests
tags: [tdd, red-phase, footer, youtube, static-site]
dependency_graph:
  requires: []
  provides: [LINK-05-test, LINK-06-test]
  affects: [SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [given-when-then, jsoup-selectFirst-css-attribute-selector]
key_files:
  created: []
  modified:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "Used .footer .footer-link[href='...'] CSS attribute selector to precisely target the YouTube link"
  - "Used existing seasonDir() helper for season subpage path — avoids fragile Files.list()"
metrics:
  duration: "3 minutes"
  completed: "2026-04-16"
  tasks_completed: 1
  files_modified: 1
---

# Phase 45 Plan 01: Footer YouTube Link — TDD RED Summary

**One-liner:** Two failing tests asserting YouTube link (href, target=_blank, rel=noopener, text) in shared footer on index page and season subpage.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Write failing tests for LINK-05 and LINK-06 | 8ef4479 | SiteGeneratorServiceTest.java |

## What Was Built

Added two new `@Test` methods to `SiteGeneratorServiceTest.java` after the existing `// --- UX-06: Footer links ---` block:

1. **`givenLayout_whenGenerate_thenFooterContainsYouTubeLink`** (LINK-05): After `generate()`, reads `index.html`, uses JSoup `selectFirst(".footer .footer-link[href='https://www.youtube.com/@CommunityTeamCup']")` to assert the YouTube link exists with `target="_blank"`, `rel="noopener"`, and text `"YouTube"`.

2. **`givenSeasonPage_whenGenerate_thenFooterContainsYouTubeLink`** (LINK-06): Same assertions on `seasonDir().resolve("standings.html")` — proves layout inheritance works on season subpages.

## RED Phase Verification

Both tests fail as expected:
- `givenLayout_whenGenerate_thenFooterContainsYouTubeLink:853 Footer should contain YouTube link ==> expected: not <null>`
- `givenSeasonPage_whenGenerate_thenFooterContainsYouTubeLink:868 Season subpage footer should contain YouTube link ==> expected: not <null>`

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — test-only changes, no production surface modified.

## Self-Check: PASSED

- [x] `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — modified, commit 8ef4479 exists
- [x] Both new test methods compile and execute
- [x] Both tests FAIL (RED) with clear assertion messages
- [x] No regressions to existing tests
