---
phase: 50-site-generator-test-robustness
plan: "01"
subsystem: sitegen
tags: [bugfix, testing, tdd, mock, broken-link]
dependency_graph:
  requires: []
  provides: [OVER-06-fix, youtube-mock-in-sitegen-tests]
  affects: [SiteGeneratorService, teams.html, SiteGeneratorServiceTest, SiteGeneratorE2ETest]
tech_stack:
  added: []
  patterns: [MockitoBean, TDD-RED-GREEN, Thymeleaf-null-guard]
key_files:
  created: []
  modified:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/main/resources/templates/site/teams.html
decisions:
  - "Used seasonTeamRepository.save(new SeasonTeam(...)) directly in test to avoid duplicate SeasonTeam inserts via season entity cascade"
  - "teamsWithProfiles computed via standingsService.calculateStandings() per season; teams with played==0 are excluded by StandingsService.removeIf"
  - "Template uses th:if guard: anchor rendered only when profileUrl != null, plain span otherwise"
metrics:
  duration: "~7 minutes"
  completed: "2026-04-17"
  tasks_completed: 2
  files_changed: 4
---

# Phase 50 Plan 01: Site Generator Test Robustness Summary

Fix the OVER-06 defect (broken links in teams.html for 0-game teams) and mock YouTubeScraperService in all integration tests to eliminate live HTTP calls.

## What Was Built

**OVER-06 Fix:** `generateTeamsOverview()` in `SiteGeneratorService` now computes which teams have generated profile pages by querying `standingsService.calculateStandings()` for each season. Teams with 0 played games are excluded by `StandingsService` (which calls `removeIf(s -> s.getPlayed() == 0)`), so they get `profileUrl = null`. The `teams.html` template renders a plain `<span>` instead of an `<a>` for teams with null profileUrl, preventing broken links.

**YouTube Mock:** Both `SiteGeneratorServiceTest` and `SiteGeneratorE2ETest` now use `@MockitoBean YouTubeScraperService` returning the deterministic video ID `"dQw4w9WgXcQ"`. This replaces all live HTTP calls to YouTube during test execution.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | TDD RED: Mock YouTube + failing 0-game test | 8aac15d | SiteGeneratorServiceTest.java, SiteGeneratorE2ETest.java |
| 2 | TDD GREEN: Fix generateTeamsOverview + template guard | 12de571 | SiteGeneratorService.java, teams.html |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] DataIntegrityViolation in 0-game team test**
- **Found during:** Task 1 (RED phase run)
- **Issue:** Test called `season.addTeam(zeroGameTeam)` then `seasonRepository.save(season)`, which triggered duplicate SeasonTeam inserts via cascade for the already-existing TNR and P1R entries.
- **Fix:** Replaced `season.addTeam()` + `seasonRepository.save()` with `seasonTeamRepository.save(new SeasonTeam(season, zeroGameTeam))` — bypasses the entity graph cascade.
- **Files modified:** SiteGeneratorServiceTest.java
- **Commit:** 8aac15d

## TDD Gate Compliance

- RED gate: commit `8aac15d` (test — failing 0-game team test)
- GREEN gate: commit `12de571` (fix — generateTeamsOverview filter + template guard)

## Known Stubs

None.

## Threat Flags

None. The `th:if` null guard on `profileUrl` (T-50-02 mitigation) is implemented. `@MockitoBean` is test-scoped only (T-50-01). Live YouTube HTTP calls eliminated (T-50-03).

## Self-Check

Files exist:
- src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java — contains `@MockitoBean`, `dQw4w9WgXcQ`, `givenTeamWithZeroGames_*`
- src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java — contains `@MockitoBean`, `dQw4w9WgXcQ`
- src/main/java/org/ctc/sitegen/SiteGeneratorService.java — contains `teamsWithProfiles`, `hasProfile`
- src/main/resources/templates/site/teams.html — contains `th:if="${entry.profileUrl() != null}"` and `th:if="${entry.profileUrl() == null}"`

Commits exist: 8aac15d, 12de571

`./mvnw verify` result: BUILD SUCCESS — 1009 tests run, 0 failures, all coverage checks met.

## Self-Check: PASSED
