---
phase: 82-backup-cleanup
plan: 04
subsystem: backup-restore
tags: [backup, restore, cleanup, lombok, annotation]
dependency_graph:
  requires: []
  provides: [IN-01-complete]
  affects: [src/main/java/org/ctc/backup/restore/entity/]
tech_stack:
  added: []
  patterns: []
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/backup/restore/entity/DriverRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/MatchRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/MatchScoringRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/MatchdayRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/PhaseTeamRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/PsnAliasRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceAttachmentRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceResultRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceScoringRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/RaceSettingsRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/SeasonDriverRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/SeasonPhaseGroupRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/SeasonPhaseRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/SeasonRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/SeasonTeamRestorer.java
    - src/main/java/org/ctc/backup/restore/entity/TeamRestorer.java
decisions:
  - "D-07: IN-01 (annotation removal) committed separately from IN-02 (default-constructor addition)"
  - "D-09: Post-removal grep confirms zero @RequiredArgsConstructor occurrences in entity restorer package"
metrics:
  duration: "~15 minutes"
  completed: "2026-05-16"
  tasks_completed: 2
  files_modified: 18
---

# Phase 82 Plan 04: IN-01 Remove no-op @RequiredArgsConstructor (18 Restorers) Summary

**One-liner:** Removed no-op `@RequiredArgsConstructor` annotation and its unused import from all 18 entity restorers that have no `private final` fields, eliminating misleading Lombok usage.

## What Was Done

Removed the dead Lombok annotation `@RequiredArgsConstructor` (and its `import lombok.RequiredArgsConstructor;`) from all 18 entity restorer classes in `src/main/java/org/ctc/backup/restore/entity/`. Each of these classes implements `EntityRestorer` via `JdbcTemplate` (received as a method parameter, not injected), has no `private final` fields, and therefore needs no constructor from Lombok. The annotation was a copy-paste artifact generating an empty no-arg constructor identical to Java's default — purely misleading for future readers.

## Files Modified (18)

| File | Change |
|------|--------|
| DriverRestorer.java | Removed `@RequiredArgsConstructor` + import |
| MatchRestorer.java | Removed `@RequiredArgsConstructor` + import |
| MatchScoringRestorer.java | Removed `@RequiredArgsConstructor` + import |
| MatchdayRestorer.java | Removed `@RequiredArgsConstructor` + import |
| PhaseTeamRestorer.java | Removed `@RequiredArgsConstructor` + import |
| PsnAliasRestorer.java | Removed `@RequiredArgsConstructor` + import |
| RaceAttachmentRestorer.java | Removed `@RequiredArgsConstructor` + import |
| RaceLineupRestorer.java | Removed `@RequiredArgsConstructor` + import |
| RaceRestorer.java | Removed `@RequiredArgsConstructor` + import |
| RaceResultRestorer.java | Removed `@RequiredArgsConstructor` + import |
| RaceScoringRestorer.java | Removed `@RequiredArgsConstructor` + import |
| RaceSettingsRestorer.java | Removed `@RequiredArgsConstructor` + import |
| SeasonDriverRestorer.java | Removed `@RequiredArgsConstructor` + import |
| SeasonPhaseGroupRestorer.java | Removed `@RequiredArgsConstructor` + import |
| SeasonPhaseRestorer.java | Removed `@RequiredArgsConstructor` + import |
| SeasonRestorer.java | Removed `@RequiredArgsConstructor` + import |
| SeasonTeamRestorer.java | Removed `@RequiredArgsConstructor` + import |
| TeamRestorer.java | Removed `@RequiredArgsConstructor` + import |

**NOT touched (IN-02 scope, plan 82-05):** CarRestorer.java, PlayoffMatchupRestorer.java, PlayoffRestorer.java, PlayoffRoundRestorer.java, PlayoffSeedRestorer.java, TrackRestorer.java

## Verification Results

| Check | Result |
|-------|--------|
| `git grep -l '@RequiredArgsConstructor' src/.../entity/` | 0 matches (CLEAN) |
| `git grep -l 'lombok.RequiredArgsConstructor' src/.../entity/` | 0 matches (CLEAN) |
| `./mvnw clean test-compile -q` | Exit 0 |
| `./mvnw test -Dtest='*Restorer*' -q` | Exit 0 |
| `./mvnw verify -Dit.test=BackupImportServiceIT -DfailIfNoTests=false -q` | Exit 0 |

## Commit

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1+2 | Remove no-op @RequiredArgsConstructor (18 restorers) | 491801bd | 18 restorer files |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — annotation-only cleanup, no new trust boundaries introduced.

## Self-Check: PASSED

- All 18 files modified: confirmed via `git log -1 --name-only` (count = 18)
- Commit `491801bd` exists: confirmed via `git rev-parse --short HEAD`
- Zero `@RequiredArgsConstructor` remaining in entity restorer package: confirmed
- Maven clean test-compile: Exit 0
- Restorer unit tests: Exit 0
- BackupImportServiceIT: Exit 0
