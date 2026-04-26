---
phase: 39-entity-cross-linking
plan: "02"
subsystem: sitegen
tags: [tdd-green, entity-cross-linking, thymeleaf, sitegen, css]
dependency_graph:
  requires: [39-01-tdd-red-gate-for-entity-links]
  provides: [entity-cross-links-all-site-pages]
  affects:
    - src/main/java/org/ctc/sitegen/model/RaceView.java
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/main/resources/static/site/css/style.css
    - src/main/resources/templates/site/standings.html
    - src/main/resources/templates/site/driver-ranking.html
    - src/main/resources/templates/site/matchday.html
    - src/main/resources/templates/site/team-profile.html
    - src/main/resources/templates/site/index.html
tech_stack:
  added: []
  patterns: [pre-computed-urls-in-service, slug-map-context-variable, tdd-green-phase]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/sitegen/model/RaceView.java
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/main/resources/static/site/css/style.css
    - src/main/resources/templates/site/standings.html
    - src/main/resources/templates/site/driver-ranking.html
    - src/main/resources/templates/site/matchday.html
    - src/main/resources/templates/site/team-profile.html
    - src/main/resources/templates/site/index.html
decisions:
  - "Pre-compute all entity URLs in service layer (slug maps) rather than computing in templates — keeps templates lean per architectural principle"
  - "Use java.util.HashMap<UUID, String> passed as Thymeleaf context variable for slug maps — simple, type-safe, no extra abstraction needed"
  - "DriverEntry record alongside SeasonEntry pattern — consistent with existing codebase record pattern"
  - "toRaceView() takes driverUrlPrefix param to support different relative path depths (matchday depth-3 vs. index depth-0)"
  - "Index matchday result driver links left without template change — index has no match-results table, test 6 passes vacuously as designed in Plan 01"
metrics:
  duration: "~25 minutes"
  completed: "2026-04-16"
  tasks: 2
  files_changed: 8
---

# Phase 39 Plan 02: Entity Cross-Linking TDD GREEN Phase Summary

Entity cross-linking implemented across all five static site templates: team names in standings link to team profiles, driver PSN-IDs in driver ranking and matchday results link to driver profiles, and team profile pages now show a Drivers section with links to each driver's profile. CSS `.entity-link` class provides accent-colored styling with hover transition.

## Tasks Completed

| Task | Description | Commit | Status |
|------|-------------|--------|--------|
| 1 | Extend RaceView, add slug maps and DriverEntry, add entity-link CSS | 36aa6a0 | Done |
| 2 | Update all five templates with entity-link anchors (TDD GREEN) | 075c5d8 | Done |

## What Was Built

### Task 1: Service and Data Layer

**`RaceView.ResultView`** extended with `String driverProfileUrl` as the last field — pre-computed in `toRaceView()` using a `driverUrlPrefix` parameter.

**`SiteGeneratorService`** changes:
- `toRaceView(Race, Season, String driverUrlPrefix)` — third param allows callers to specify relative path prefix per page depth
- `generateMatchdays()` passes `"../driver/"` (matchday pages at depth 3)
- `generateIndex()` passes `"./season/{seasonSlug}/driver/"` (index at depth 0)
- `generateStandings()` computes `teamSlugMap: UUID → "team/{tSlug}.html"` and passes it via context
- `generateDriverRanking()` computes `driverSlugMap: UUID → "driver/{dSlug}.html"` and passes it via context
- `generateIndex()` computes `indexTeamSlugMap: UUID → "./season/{sSlug}/team/{tSlug}.html"` and passes it as `teamSlugMap`
- `generateTeamProfiles()` loads `SeasonDriver` entries per team, computes `totalPoints` from `RaceResult`, builds `DriverEntry` list and passes as `drivers` context variable
- `DriverEntry(String psnId, String driverProfileUrl, int totalPoints)` record added alongside `SeasonEntry`

**`style.css`** — `.entity-link` class added after `tr:hover` rule:
- Default: `color: var(--accent)` (#4fc3f7), no underline
- Hover: `color: #b3e5fc` (lighter shade), underline

### Task 2: Template Updates

| Template | Change | Requirement |
|----------|--------|-------------|
| `standings.html` | `<strong class="text-white">` → `<a class="entity-link font-bold" th:href="${teamSlugMap.get(s.team.id)}">` | CONT-02 |
| `driver-ranking.html` | `td class="font-bold text-white"` → `<a class="entity-link" th:href="${driverSlugMap.get(r.driver.id)}">` inside td | CONT-03 |
| `matchday.html` | `td class="font-bold"` with text → `<a class="entity-link" th:href="${result.driverProfileUrl}">` inside td | CONT-04 |
| `team-profile.html` | New Drivers section with `th:each="d : ${drivers}"` table and `<a class="entity-link font-bold" th:href="${d.driverProfileUrl}">` | CONT-08 |
| `index.html` | `td class="font-bold text-white"` → `<a class="entity-link" th:href="${teamSlugMap.get(s.team.id)}">` inside td | D-04 |

## TDD Gate Compliance

- RED gate commit: `f4cd388` (Plan 01) — 5 failing tests
- GREEN gate commit: `075c5d8` — all 34 tests pass (28 existing + 6 new)

Test results after Task 2:
- Tests run: 34 | Failures: 0 | Errors: 0 | Skipped: 0
- Full suite: 959 tests, 0 failures, JaCoCo coverage checks met

## Deviations from Plan

### Deviation 1: Maven working directory issue

**Found during:** Task 2 verification (first test run)
**Issue:** First test run used `cd /Users/jegr/Documents/github/ctc-manager && ./mvnw test` (main project directory). Maven compiled templates from the main project's `src/`, not the worktree's `src/`. The worktree's updated templates were never compiled into `target/classes/`. Tests read stale HTML without entity-link elements and appeared to fail.
**Fix:** Ran `./mvnw test` from inside the worktree directory (cwd `/Users/jegr/Documents/github/ctc-manager/.claude/worktrees/agent-a64eaa35/`). All 34 tests passed on first correct run.
**Impact:** No code changes needed. Implementation was correct from the start.

## Known Stubs

None. All entity links are pre-computed from DB data and wired into templates.

## Threat Flags

None. All URLs are pre-computed server-side via `slugify()` which strips non-alphanumeric characters. No user input reaches URL construction. Threat T-39-03 (slug injection) is fully mitigated by the existing `slugify()` method.

## Self-Check: PASSED

- [x] `RaceView.java` contains `String driverProfileUrl` in the ResultView record
- [x] `SiteGeneratorService.java` method `toRaceView` has 3 parameters (Race, Season, String)
- [x] `SiteGeneratorService.java` contains `record DriverEntry(String psnId, String driverProfileUrl, int totalPoints)`
- [x] `SiteGeneratorService.java` `generateStandings()` contains `ctx.setVariable("teamSlugMap"`
- [x] `SiteGeneratorService.java` `generateDriverRanking()` contains `ctx.setVariable("driverSlugMap"`
- [x] `SiteGeneratorService.java` `generateIndex()` contains `ctx.setVariable("teamSlugMap"`
- [x] `SiteGeneratorService.java` `generateTeamProfiles()` contains `ctx.setVariable("drivers"`
- [x] `SiteGeneratorService.java` `generateMatchdays()` contains `toRaceView(r, season, "../driver/")`
- [x] `SiteGeneratorService.java` `generateIndex()` contains `"./season/" + activeSeasonSlug + "/driver/"` in toRaceView call
- [x] `style.css` contains `.entity-link {` and `color: var(--accent);`
- [x] `style.css` contains `.entity-link:hover {` and `color: #b3e5fc;`
- [x] `standings.html` contains `entity-link` (count: 1)
- [x] `driver-ranking.html` contains `entity-link` (count: 1)
- [x] `matchday.html` contains `entity-link` (count: 1)
- [x] `team-profile.html` contains `entity-link` (count: 1)
- [x] `index.html` contains `entity-link` (count: 1)
- [x] Commit 36aa6a0 exists in git log
- [x] Commit 075c5d8 exists in git log
- [x] `./mvnw test -Dtest=SiteGeneratorServiceTest` shows 34 tests passing, 0 failures
- [x] `./mvnw verify` shows BUILD SUCCESS with 959 tests and coverage >= 82%
