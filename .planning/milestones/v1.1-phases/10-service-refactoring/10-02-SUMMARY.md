---
phase: 10-service-refactoring
plan: 02
subsystem: playoff
tags: [refactoring, service-split, srp, no-circular-deps]
dependency_graph:
  requires: []
  provides: [PlayoffBracketViewService, PlayoffSeedingService]
  affects: [PlayoffService, PlayoffController, SiteGeneratorService, ScoringService]
tech_stack:
  added: [PlayoffBracketViewService, PlayoffSeedingService]
  patterns: [Single Responsibility Principle, Service Decomposition, Shared ScoringService helper]
key_files:
  created:
    - src/main/java/org/ctc/domain/service/PlayoffBracketViewService.java
    - src/main/java/org/ctc/domain/service/PlayoffSeedingService.java
    - src/test/java/org/ctc/domain/service/PlayoffBracketViewServiceTest.java
    - src/test/java/org/ctc/domain/service/PlayoffSeedingServiceTest.java
  modified:
    - src/main/java/org/ctc/domain/service/PlayoffService.java
    - src/main/java/org/ctc/domain/service/ScoringService.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/test/java/org/ctc/domain/service/PlayoffServiceTest.java
    - src/test/java/org/ctc/domain/service/ScoringServiceTest.java
    - src/test/java/org/ctc/admin/controller/PlayoffControllerTest.java
decisions:
  - "calculateTeamTotals moved to ScoringService (per D-06) to break circular dependency between PlayoffService and PlayoffBracketViewService"
  - "SiteGeneratorService updated to inject PlayoffBracketViewService for getBracketView (not in original plan scope, auto-fixed per Rule 3)"
  - "PlayoffService.seedTeam removed (moved to PlayoffSeedingService); PlayoffServiceTest updated to use playoffSeedingService"
metrics:
  duration: 25m
  completed: 2026-04-06
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 7
---

# Phase 10 Plan 02: PlayoffService Split Summary

Split PlayoffService (621 lines) into three focused services: PlayoffBracketViewService (bracket view assembly), PlayoffSeedingService (seeding logic), and a slimmed-down PlayoffService (matchup lifecycle + CRUD) with calculateTeamTotals moved to ScoringService to avoid circular dependencies.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extract PlayoffBracketViewService and PlayoffSeedingService | e578b67 | PlayoffBracketViewService.java, PlayoffSeedingService.java, PlayoffService.java, ScoringService.java + 5 test files + SiteGeneratorService |
| 2 | Update PlayoffController to inject new services | b08f037 | PlayoffController.java, PlayoffControllerTest.java |

## Result

- `PlayoffService`: 339 lines (down from 621) — matchup lifecycle + CRUD only
- `PlayoffBracketViewService`: 161 lines — getBracketView + view classes
- `PlayoffSeedingService`: 177 lines — seeding logic (autoSeedBracket, saveSeed, saveSeedNumbers, getSeedingData, seedTeam)
- `ScoringService`: added `calculateTeamTotals` (shared helper, per D-06 no duplication)

## Dependency Graph (no circular deps)

```
ScoringService (no service deps)
  ^
  |
PlayoffBracketViewService -> ScoringService
  ^
  |
PlayoffSeedingService -> PlayoffBracketViewService
  |
PlayoffService -> PlayoffBracketViewService, ScoringService
  |
PlayoffController -> PlayoffService, PlayoffBracketViewService, PlayoffSeedingService
SiteGeneratorService -> PlayoffBracketViewService
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] SiteGeneratorService.getBracketView call needed update**
- **Found during:** Task 1
- **Issue:** SiteGeneratorService called `playoffService.getBracketView()` which was removed from PlayoffService
- **Fix:** Added `PlayoffBracketViewService` injection to SiteGeneratorService and updated the call
- **Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
- **Commit:** e578b67

## Known Stubs

None.

## Threat Flags

None — pure internal service refactoring, no new trust boundaries or endpoints introduced.

## Self-Check: PASSED

- [x] PlayoffBracketViewService.java exists
- [x] PlayoffSeedingService.java exists
- [x] PlayoffBracketViewServiceTest.java exists
- [x] PlayoffSeedingServiceTest.java exists
- [x] Commit e578b67 exists
- [x] Commit b08f037 exists
- [x] 769 tests pass, JaCoCo coverage checks met
- [x] No circular dependencies (Spring context starts cleanly)
