---
phase: 37-critical-link-fixes
plan: "02"
subsystem: sitegen
tags: [tdd, green-phase, link-fixes, site-generator]
dependency_graph:
  requires: [37-01]
  provides: []
  affects: [SiteGeneratorService, layout.html, archive.html, team-profile.html]
tech_stack:
  added: []
  patterns: [TDD-GREEN, SeasonEntry-record, path-traversal-guard, rootPath-dot-default]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/main/resources/templates/site/layout.html
    - src/main/resources/templates/site/archive.html
    - src/main/resources/templates/site/team-profile.html
decisions:
  - "SeasonEntry inner record wraps Season+slug, following RaceView record pattern — pre-computed at service level, no Thymeleaf string manipulation"
  - "activeSeasonSlug propagated via writeTemplate() 4th parameter to all 8 generate*() methods — single control point in generate()"
  - "rootPath defaults to '.' (not empty string) for root-level pages — standard relative-path convention"
  - "copyLogoToAssets() follows TeamCardService.encodeLogoBase64() path-traversal guard pattern exactly"
  - "teamLogoRelPath context variable instead of mutating team.setLogoUrl() — prevents OSIV entity persistence"
metrics:
  duration: "~7 minutes"
  completed: "2026-04-16T08:06:28Z"
  tasks_completed: 3
  tasks_total: 3
  files_changed: 4
requirements: [LINK-01, LINK-02, LINK-03, LINK-04]
---

# Phase 37 Plan 02: TDD GREEN Phase — Implement All Four Link Fixes Summary

TDD GREEN phase: all four LINK fixes implemented in SiteGeneratorService and three Thymeleaf templates updated. Archive season links, Driver Ranking nav, root-level relative paths, and team logos all resolve correctly. All 16 SiteGeneratorServiceTest tests pass; full suite (941 tests) passes with coverage checks met.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | LINK-02 activeSeasonSlug propagation + LINK-03 rootPath dot fix | ced0aca | SiteGeneratorService.java, layout.html |
| 2 | LINK-01 SeasonEntry record + archive slug fix | ced0aca | SiteGeneratorService.java, archive.html |
| 3 | LINK-04 copyLogoToAssets() + team logo rewrite | ced0aca | SiteGeneratorService.java, team-profile.html |

Tasks 1–3 were committed atomically as one fix commit (all changes to SiteGeneratorService.java are interdependent through the refactored method signatures).

## What Was Built

### LINK-03: rootPath dot fix

In `writeTemplate()`, `rootPath` now defaults to `"."` when the computed relative path is empty (root-level pages):

```java
String rootStr = relativeRoot.toString().replace('\\', '/');
context.setVariable("rootPath", rootStr.isEmpty() ? "." : rootStr);
```

Root-level pages (`index.html`, `archive.html`) now produce `./index.html` instead of `/index.html`.

### LINK-02: activeSeasonSlug propagation

`writeTemplate()` signature extended to 4 parameters including `activeSeasonSlug`. All 8 `generate*()` methods updated to accept and pass `activeSeasonSlug`. Active slug computed once in `generate()`:

```java
String activeSeasonSlug = activeSeason != null ? slugify(activeSeason.getDisplayLabel()) : "";
```

`layout.html` Driver Ranking nav link updated with `th:if` guard:

```html
<a th:if="${activeSeasonSlug != null and !#strings.isEmpty(activeSeasonSlug)}"
   th:href="${rootPath + '/season/' + activeSeasonSlug + '/driver-ranking.html'}">Driver Ranking</a>
```

### LINK-01: SeasonEntry record + archive slug

Added `record SeasonEntry(Season season, String slug) {}` inside `SiteGeneratorService`. `generateArchive()` now maps seasons to `SeasonEntry` list with pre-computed slugs using the service's `slugify(s.getDisplayLabel())`. `archive.html` iterates `seasonEntries` using `entry.slug` for the Standings href.

### LINK-04: copyLogoToAssets() + teamLogoRelPath

Added `copyLogoToAssets(String logoUrl, Path outPath, String assetsPath)` method:
- Path-traversal guard: `logoFile.startsWith(uploadBase)` check (follows `TeamCardService.encodeLogoBase64()` pattern)
- Missing logos logged at `warn` level and return null (no generation failure)
- Copies to `assets/img/logos/{relativePart}` preserving UUID subdirectory structure
- Returns relative path string for template use

`generateTeamProfiles()` computes `assetsPath` for the team directory level, calls `copyLogoToAssets()`, and sets `teamLogoRelPath` context variable (never mutates `team.setLogoUrl()`).

`team-profile.html` now uses `teamLogoRelPath`:

```html
<img th:if="${teamLogoRelPath}" th:src="${teamLogoRelPath}" class="team-logo" th:alt="${team.shortName}">
```

## Test Results

| Category | Count | Status |
|----------|-------|--------|
| New LINK tests (GREEN) | 4 | PASS |
| Pre-existing SiteGeneratorServiceTest | 12 | PASS |
| Total in SiteGeneratorServiceTest | 16 | PASS |
| Full test suite | 941 | PASS |
| JaCoCo coverage check | — | PASS (>= 82%) |

## TDD Gate Compliance

- RED gate: `test(sitegen): add failing tests for LINK-01..04 link fixes` (commit a76d489, Plan 01) ✓
- GREEN gate: `fix(sitegen): fix archive slugs, nav links, relative paths, and team logos` (commit ced0aca) ✓
- REFACTOR gate: not needed — implementation is clean

## Deviations from Plan

### Deviation: Maven must run from worktree directory

During verification, tests continued to fail after edits because `cd /Users/jegr/Documents/github/ctc-manager && ./mvnw test` compiled the main working tree's unmodified source files, not the worktree's modified files. Fixed by running `./mvnw` from the worktree directory (cwd). No code change required — process deviation only.

All other aspects executed exactly as written.

## Known Stubs

None — all four LINK fixes are fully wired. Team logo path is computed from real `team.getLogoUrl()` at generation time.

## Threat Flags

No new threat surface beyond what was modeled in the plan's threat model (T-37-02 and T-37-03 mitigated by path-traversal guard in `copyLogoToAssets()`).

## Self-Check

### Created/modified files exist:

```
[ -f "src/main/java/org/ctc/sitegen/SiteGeneratorService.java" ] -> FOUND
[ -f "src/main/resources/templates/site/layout.html" ] -> FOUND
[ -f "src/main/resources/templates/site/archive.html" ] -> FOUND
[ -f "src/main/resources/templates/site/team-profile.html" ] -> FOUND
```

### Commits exist:

- ced0aca (fix(sitegen): fix archive slugs, nav links, relative paths, and team logos) -> FOUND

## Self-Check: PASSED
