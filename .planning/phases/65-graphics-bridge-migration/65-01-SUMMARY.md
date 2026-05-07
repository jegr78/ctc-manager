---
plan_id: 65-01
phase: 65
phase_name: graphics-bridge-migration
title: Migrate 5 graphics callers to phase-aware StandingsService API
subsystem: admin-service / graphics
tags: [refactoring, standings, phase-aware, graphics, migration]
dependency_graph:
  requires: []
  provides: [65-01-callers-migrated]
  affects: [StandingsService bridge caller count]
tech_stack:
  added: []
  patterns:
    - matchday.getPhase().getId() + matchday.getGroup() != null ? ... : null (D-06)
    - seasonPhaseService.findRegularPhase(seasonId) + canonical API (D-07)
    - Mockito verify(standingsService).calculateStandings(eq(phaseId), isNull()) (D-11)
    - Mockito verify(standingsService).calculateStandings(eq(phaseId), eq(groupId)) (D-12)
key_files:
  created:
    - src/test/java/org/ctc/admin/service/SettingsGraphicServiceTest.java
  modified:
    - src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java
    - src/main/java/org/ctc/admin/service/TeamCardService.java
    - src/main/java/org/ctc/admin/service/OverlayGraphicService.java
    - src/main/java/org/ctc/admin/service/SettingsGraphicService.java
    - src/main/java/org/ctc/admin/service/LineupGraphicService.java
    - src/test/java/org/ctc/admin/service/AbstractMatchdayGraphicServiceTest.java
    - src/test/java/org/ctc/admin/service/TeamCardServiceTest.java
    - src/test/java/org/ctc/admin/service/OverlayGraphicServiceTest.java
    - src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java
decisions:
  - D-06: matchday/race callers use matchday.getPhase().getId() + group null-conditional
  - D-07: TeamCardService uses seasonPhaseService.findRegularPhase(seasonId).getId() + null
  - D-08: Inline at each call site — no shared helper extracted
  - D-10: 8 Mockito stubs rewritten in AbstractMatchdayGraphicServiceTest
  - D-11: 5 LEAGUE-regression tests added (one per caller)
  - D-12: 3 GROUPS-tests added (Abstract, Overlay, Settings)
  - D-13: Mockito unit-test style only — no new @SpringBootTest ITs
metrics:
  duration: ~45 minutes
  completed: 2026-05-07
  tasks_total: 5
  tasks_completed: 5
  files_created: 1
  files_modified: 9
---

# Phase 65 Plan 01: Migrate 5 graphics callers to phase-aware StandingsService API — Summary

**One-liner:** Migrated all 5 graphics services from `calculateStandings(seasonId)` bridge to canonical `calculateStandings(phaseId, groupId)` API, with 8 stub rewrites and 8 new contract tests.

## Callers Migrated (D-03a..e)

### D-03a: AbstractMatchdayGraphicService (line 47 → lines 47-51)

Before:
```java
var season = matchday.getSeason();
var standings = standingsService.calculateStandings(season.getId());
```

After:
```java
var season = matchday.getSeason();
var phase = matchday.getPhase();
var group = matchday.getGroup();
var standings = standingsService.calculateStandings(
        phase.getId(),
        group != null ? group.getId() : null);
```

Note: `var season` kept — still used for `season.getName()` and `season.getYear()` in the return statement.

### D-03b: TeamCardService (line 52 → lines 52-54) + constructor change

Constructor change: added `SeasonPhaseService seasonPhaseService` as 3rd parameter (before `@Value`).

Before:
```java
var standings = standingsService.calculateStandings(season.getId());
```

After:
```java
var regularPhase = seasonPhaseService.findRegularPhase(season.getId());
var standings = standingsService.calculateStandings(regularPhase.getId(), null);
```

### D-03c: OverlayGraphicService (line 58 → lines 52-58)

Before:
```java
var season = race.getMatchday().getSeason();
...
var standings = standingsService.calculateStandings(season.getId());
```

After:
```java
var season = race.getMatchday().getSeason();
var phase = race.getMatchday().getPhase();
var group = race.getMatchday().getGroup();
...
var standings = standingsService.calculateStandings(
        phase.getId(),
        group != null ? group.getId() : null);
```

### D-03d: SettingsGraphicService (line 67 → lines 67-72, inside else branch)

Before:
```java
} else {
    var standings = standingsService.calculateStandings(season.getId());
```

After:
```java
} else {
    var phase = race.getMatchday().getPhase();
    var group = race.getMatchday().getGroup();
    var standings = standingsService.calculateStandings(
            phase.getId(),
            group != null ? group.getId() : null);
```

### D-03e: LineupGraphicService (line 70 → lines 70-75, inside else branch)

Same pattern as SettingsGraphicService — non-playoff else branch only; playoff branch unaffected.

## TeamCardService Constructor Signature Change (D-07 / Pitfall 4)

- **Before:** `TeamCardService(TemplateEngine, StandingsService, @Value String uploadDir)`
- **After:** `TeamCardService(TemplateEngine, StandingsService, SeasonPhaseService, @Value String uploadDir)`
- Spring DI auto-wires `SeasonPhaseService` — no `@Bean` changes needed.
- `TeamCardServiceTest` constructor calls updated from `new TeamCardService(null, null, "uploads")` to `new TeamCardService(null, null, null, "uploads")`.

## 8 Stub Rewrites in AbstractMatchdayGraphicServiceTest (D-10)

All 8 occurrences of:
```java
when(standingsService.calculateStandings(season.getId())).thenReturn(...)
```
rewritten to:
```java
when(standingsService.calculateStandings(eq(matchday.getPhase().getId()), isNull())).thenReturn(...)
```

Added imports: `org.mockito.ArgumentMatchers.eq`, `org.mockito.ArgumentMatchers.isNull`, `org.mockito.Mockito.verify`.

## 5 LEAGUE-Regression Tests Added (D-11)

| Test Method | File |
|---|---|
| `givenLeagueSeason_whenGenerateCard_thenStandingsCalledWithRegularPhaseIdAndNullGroup` | TeamCardServiceTest.java |
| `givenLeagueLayoutMatchday_whenPrepareBaseContext_thenStandingsCalledWithPhaseIdAndNullGroup` | AbstractMatchdayGraphicServiceTest.java |
| `givenLeagueRace_whenGenerateOverlay_thenStandingsCalledWithPhaseIdAndNullGroup` | OverlayGraphicServiceTest.java |
| `givenLeagueRace_whenGenerateSettings_thenStandingsCalledWithPhaseIdAndNullGroup` | SettingsGraphicServiceTest.java |
| `givenLeagueRace_whenGenerateLineup_thenStandingsCalledWithPhaseIdAndNullGroup` | LineupGraphicServiceTest.java |

## 3 GROUPS-Tests Added (D-12)

| Test Method | File |
|---|---|
| `givenGroupsLayoutMatchday_whenPrepareBaseContext_thenStandingsCalledWithPhaseAndMatchdayGroup` | AbstractMatchdayGraphicServiceTest.java |
| `givenGroupsLayoutRace_whenGenerateOverlay_thenStandingsCalledWithPhaseAndMatchdayGroup` | OverlayGraphicServiceTest.java |
| `givenGroupsLayoutRace_whenGenerateSettings_thenStandingsCalledWithPhaseAndMatchdayGroup` | SettingsGraphicServiceTest.java |

## NEW FILE: SettingsGraphicServiceTest.java

Created from scratch (confirmed missing per RESEARCH A2). Contains:
- `givenRaceWithoutTeams_whenGenerateSettings_thenThrowsIllegalState` — precondition guard
- `givenLeagueRace_whenGenerateSettings_thenStandingsCalledWithPhaseIdAndNullGroup` — D-11 LEAGUE
- `givenGroupsLayoutRace_whenGenerateSettings_thenStandingsCalledWithPhaseAndMatchdayGroup` — D-12 GROUPS

Helper `createReadyRace(boolean groupAttached)` provides complete `Car`, `Track`, `RaceSettings` setup and writes card PNG files to `@TempDir` so `encodeCardBase64` does not return `null` before the standings call.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] LineupGraphicService card-not-found precedes standings call**
- **Found during:** Task 3 (LineupGraphicServiceTest run)
- **Issue:** The plan's test template used `try { service.generateLineup(race); } catch (IllegalStateException ignored) {}` assuming card-not-found fires after the standings call. In fact `encodeCardBase64` returns `null` (→ `IllegalStateException`) before the `else` branch where standings is called. Zero invocations on the mock — verify failed.
- **Fix:** Created card PNG files in `@TempDir` (`tempDir/team-cards/{seasonId}/H.png` and `A.png`) before calling `generateLineup`. Same fix applied for `SettingsGraphicService.generateSettings` in Task 4.
- **Files modified:** LineupGraphicServiceTest.java, SettingsGraphicServiceTest.java
- **Commit:** 8ad7952

**2. [Rule 3 - Blocking] Race constructor `new Race(matchday, home, away, 1)` does not exist**
- **Found during:** Task 3 (plan's test template used 4-arg Race constructor)
- **Issue:** `Race` only has `@NoArgsConstructor`. The plan's test snippets referenced a constructor that doesn't exist.
- **Fix:** Used `new Race()` + setters (`setMatchday`, `setHomeTeamOverride`, `setAwayTeamOverride`, `setId`).
- **Files modified:** OverlayGraphicServiceTest.java, LineupGraphicServiceTest.java, SettingsGraphicServiceTest.java

## Test Results

```
./mvnw test -Dtest='AbstractMatchdayGraphicServiceTest,TeamCardServiceTest,OverlayGraphicServiceTest,SettingsGraphicServiceTest,LineupGraphicServiceTest'
```
Result: **All 5 targeted test classes PASSED** (exit 0).

## Bridge Call Count After Plan 65-01

- **admin/service/ scope:** 0 (plan goal achieved)
- **Project-wide SC1 grep count:** 2 remaining
  - `StandingsService.calculateBuchholzScores` (private helper) — closed in Plan 65-02
  - `SwissPairingService.calculateBuchholz` (dead public method) — deleted in Plan 65-02
- **StandingsService bridge method:** INTACT (deletion is Plan 65-03)

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. This is a pure internal API refactoring. No threat flags.

## Known Stubs

None — all 5 callers wire through to the canonical `calculateStandings(phaseId, groupId)` path. No placeholder data flows to UI rendering.

## Self-Check

### Created files exist:
- [x] `src/test/java/org/ctc/admin/service/SettingsGraphicServiceTest.java` — FOUND

### Commits exist:
- [x] `8ad7952` — refactor(65): migrate graphics services to phase-aware standings API — FOUND

## Self-Check: PASSED
