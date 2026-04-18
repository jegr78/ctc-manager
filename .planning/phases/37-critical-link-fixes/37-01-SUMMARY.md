---
phase: 37-critical-link-fixes
plan: "01"
subsystem: sitegen
tags: [tdd, red-phase, link-fixes, site-generator]
dependency_graph:
  requires: []
  provides: [37-02]
  affects: [SiteGeneratorService, SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [TDD-RED, JUnit5-integration-test, Jsoup-html-assertion, TempDir]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "uploadDir field follows exact same @lombok.Setter + @Value pattern as outputDir, enabling test injection via setter"
  - "LINK-02 test asserts on index.html nav links (root-level page), which is the most observable location"
  - "LINK-03 test checks a[href^='/'] on index.html — correct RED since rootPath is empty string at root level"
metrics:
  duration: "~8 minutes"
  completed: "2026-04-16T09:57Z"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 2
requirements: [LINK-01, LINK-02, LINK-03, LINK-04]
---

# Phase 37 Plan 01: TDD RED Phase — Four Failing Tests for Link Bugs Summary

TDD RED phase: uploadDir field added to SiteGeneratorService and four executable failing tests written for LINK-01..04 broken link bugs in the static site generator.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add uploadDir field to SiteGeneratorService | b9f6bc5 | SiteGeneratorService.java |
| 2 | Write four failing tests for LINK-01..04 | a76d489 | SiteGeneratorServiceTest.java |

## What Was Built

### Task 1 — uploadDir field (b9f6bc5)

Added `uploadDir` field to `SiteGeneratorService` following the exact same pattern as `outputDir`:

```java
@lombok.Setter
@Value("${app.upload-dir:data/dev/uploads}")
private String uploadDir;
```

Placed directly after `outputDir` (line 50-52). Enables LINK-04 test to inject a temp uploads directory via `siteGeneratorService.setUploadDir(...)`. No methods using this field yet — that is Plan 02's scope.

### Task 2 — Four failing tests (a76d489)

Added four new `@Test` methods to `SiteGeneratorServiceTest`:

1. **`givenSeason_whenGenerate_thenArchiveContainsCorrectSeasonSlug`** (LINK-01)
   - Asserts `archive.html` contains `a[href*='season/2026-1-gen-test-{suffix}/standings.html']`
   - Currently fails: archive uses `slugify(s.name)` → `gen-test-{suffix}`, not `slugify(s.getDisplayLabel())` → `2026-1-gen-test-{suffix}`

2. **`givenActiveSeason_whenGenerate_thenNavDriverRankingLinksToActiveSeason`** (LINK-02)
   - Asserts nav in `index.html` has `a[href*='season/{slug}/driver-ranking.html']`
   - Currently fails: nav has `/driver-ranking.html` (static root-relative)

3. **`givenActiveSeason_whenGenerate_thenRootPagesHaveNoAbsolutePaths`** (LINK-03)
   - Asserts `index.html` has zero `a[href^='/']` elements
   - Currently fails: root-level `rootPath` resolves to empty string → links become `/index.html`, `/archive.html`, etc.

4. **`givenTeamWithLogo_whenGenerate_thenLogoCopiedAndLinkedRelatively`** (LINK-04)
   - Creates fake logo at `uploads/teams/test-uuid/test-logo.png`, injects uploadDir, sets team `logoUrl = /uploads/teams/test-uuid/test-logo.png`
   - Asserts logo copied to `assets/img/logos/teams/test-uuid/test-logo.png`
   - Asserts team-profile `img.team-logo` src contains `img/logos/` and does NOT start with `/uploads/`
   - Currently fails: no copy logic exists, `team.logoUrl` rendered as-is from entity

## Test Results

| Category | Count | Status |
|----------|-------|--------|
| New failing tests (RED) | 4 | FAIL (expected) |
| Pre-existing tests | 12 | PASS |
| Total in SiteGeneratorServiceTest | 16 | 12 pass / 4 fail |

## TDD Gate Compliance

- RED gate: `test(sitegen): add failing tests for LINK-01..04 link fixes` (commit a76d489) ✓
- GREEN gate: pending (Plan 02)
- REFACTOR gate: pending (Plan 02)

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check

### Created/modified files exist:

```
[ -f "src/main/java/org/ctc/sitegen/SiteGeneratorService.java" ] → FOUND
[ -f "src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java" ] → FOUND
```

### Commits exist:

- b9f6bc5 (chore(37-01): add uploadDir field) → FOUND
- a76d489 (test(sitegen): add failing tests for LINK-01..04) → FOUND

## Self-Check: PASSED
