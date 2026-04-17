---
phase: 46-configurable-links-page
plan: "01"
subsystem: sitegen-tests
tags: [tdd, red-phase, sitegen, links-page]
dependency_graph:
  requires: []
  provides: [failing-tests-LINK-07-10]
  affects: [SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [SpringBootTest+TempDir+JSoup assertion pattern]
key_files:
  created: []
  modified:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "Empty-state test (LINK-09) uses commented-out content assertion to remain compilable without SiteProperties; Plan 02 will uncomment and wire the injection"
metrics:
  duration: "4 minutes"
  completed: "2026-04-17T05:08:34Z"
  tasks_completed: 1
  tasks_total: 1
  files_changed: 1
requirements:
  - LINK-07
  - LINK-08
  - LINK-09
  - LINK-10
---

# Phase 46 Plan 01: TDD RED Phase — Failing Tests for Configurable Links Page Summary

**One-liner:** Four failing tests in SiteGeneratorServiceTest establishing the contract for links.html page generation (LINK-07 through LINK-10).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Write failing tests for LINK-07 through LINK-10 | 7c70cd2 | SiteGeneratorServiceTest.java |

## What Was Built

Added four new `@Test` methods to `SiteGeneratorServiceTest.java` under a `// --- Phase 46: Configurable Links Page ---` section comment:

1. **`whenGenerate_thenCreatesLinksPage`** (LINK-07): Asserts `links.html` exists in the output root after `generate()`.
2. **`whenGenerate_thenLinksPageContainsConfiguredLinks`** (LINK-08+09): Asserts `.link-card` elements are present with `target="_blank"` and `rel="noopener"` on anchor elements.
3. **`givenNoLinksConfigured_whenGenerate_thenLinksPageShowsEmptyState`** (LINK-09): Asserts `links.html` exists even with no configured links; content assertion for the empty-state message is commented out pending `SiteProperties` availability in Plan 02.
4. **`whenGenerate_thenLinksPageHasSharedLayout`** (LINK-10): Asserts `nav.nav`, `footer.footer`, and `.breadcrumb` are present in the rendered page.

## RED Phase Verification

- **4 new tests FAIL** — all fail at `assertTrue(Files.exists(...links.html...))` because `generateLinks()` does not exist yet in `SiteGeneratorService`.
- **54 existing tests PASS** — no regressions introduced.
- Total run: 58 tests, 4 failures, 0 errors.

Failure messages are clear and specific:
- `"links.html should exist in output root"`
- `"links.html must exist"`
- `"links.html must exist even with no configured links"`

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this is a test-only plan. No production code was added.

## Threat Flags

None — no production code, no new network endpoints or security surface introduced.

## Self-Check: PASSED

- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — modified (69 lines added)
- Commit `7c70cd2` exists in git log
- 4 new tests present, all fail in RED phase
- 54 existing tests unaffected
