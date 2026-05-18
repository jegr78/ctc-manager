---
phase: "79"
plan: "02a"
subsystem: "admin.controller + backup.config + backup.io + backup.security"
tags: [cleanup, comment-thinning, dead-code-removal]
dependency_graph:
  requires: []
  provides: [79-02a-leaf-packages-cleaned]
  affects: []
tech_stack:
  added: []
  patterns: [comment-thinning-D09, dead-code-removal-D04, schutzwortliste-guard]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/admin/controller/SeasonPhaseController.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/main/java/org/ctc/admin/controller/MatchdayController.java
    - src/main/java/org/ctc/admin/controller/RaceLineupController.java
    - src/test/java/org/ctc/admin/controller/SeasonPhaseControllerTest.java
    - src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java
    - src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java
    - src/main/java/org/ctc/backup/io/LimitedInputStream.java
    - src/main/java/org/ctc/backup/security/PathTraversalGuard.java
decisions:
  - "Dead StandingsService injection removed from SeasonPhaseController (grep: 0 callsites)"
  - "Dead PlayoffBracketViewService injection removed from PlayoffController (grep: 0 callsites in controller)"
  - "No extract-method applied — all candidates had awkward early-return branching (CD-02)"
metrics:
  duration: "~45 min"
  completed: "2026-05-15"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 79 Plan 02a: Cleanup Leaf Packages (admin.controller + backup leaves) Summary

Comment-thinning and dead-code removal across org.ctc.admin.controller (23 files) and three
backup leaf packages (backup.config, backup.io, backup.security), removing phase-history noise
without touching behavioral content.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Cleanup org.ctc.admin.controller | 20b3d34 | 5 files (3 comment-thin, 2 dead-code) |
| 2 | Cleanup backup.config + backup.io + backup.security | b1e7427, 18f1ea9, f4b8714 | 4 files (comment-thin only) |

## Changes by Package

### org.ctc.admin.controller (Task 1 — commit 20b3d34)

**Comment-thinning (3 files):**
- `MatchdayController.java`: removed was-comment paraphrasing model.addAttribute lines
- `RaceLineupController.java`: removed was-comment paraphrasing loop logic
- `SeasonPhaseControllerTest.java`: removed tombstone "All tests RED in Wave 0" from class Javadoc

**Dead-code removal (D-04, 2 files):**
- `SeasonPhaseController.java`: removed unused `StandingsService` import + field (grep: 0 callsites)
- `PlayoffController.java`: removed unused `PlayoffBracketViewService` import + field (grep: 0 callsites in controller — used in PlayoffService but not PlayoffController)

**Schutzwort preserved:**
- `SeasonPhaseControllerTest.java` line 56: OSIV comment retained

### org.ctc.backup.config (Task 2)

**BackupObjectMapperConfig.java** (committed in b1e7427 by concurrent agent — identical to plan):
- Stripped "Phase 72 — " prefix from class Javadoc title
- Removed Phase 72/73 evolution paragraph (future tense no longer true)
- Simplified `backupObjectMapper` Javadoc (removed phase-numbered list format)
- Preserved `§Pitfall P-2` line (Schutzwort: pitfall)
- Preserved `defaultObjectMapper` Javadoc (Schutzwort: transitive)

**BackupObjectMapperConfigIT.java** (committed in b1e7427 by concurrent agent):
- Stripped "Phase 72 / Plan 03 — Wave 0 stub" and "RED until task 2 lands" from class Javadoc
- Removed (D-11) labels from assertion messages

### org.ctc.backup.io (Task 2)

**LimitedInputStream.java** (committed in 18f1ea9 by concurrent agent — identical to plan):
- Removed `(CONTEXT §D-12, §specifics)` from ZipEntry trust problem h3 heading
- Removed `(Plan 04 — ` prefix from canonical call site h3 heading

### org.ctc.backup.security (Task 2)

**PathTraversalGuard.java** (committed in f4b8714):
- Stripped SECU-01 requirement reference from class Javadoc
- Removed `(D-11 discretion — own class for reuse + unit-test isolation)` parenthetical
- Removed `(REQUIREMENTS SECU-01: "Wiederverwendung statt Duplikat")` from Predicate h3
- Simplified symlink-TOCTOU note: removed `(SECU-01 reuse)` and `(T-74-02-05):` references
- All behavioral content preserved

## Deviations from Plan

### Note on Concurrent Agent Coordination

The three backup leaf package files (BackupObjectMapperConfig, BackupObjectMapperConfigIT,
LimitedInputStream) were cleaned up by concurrent agents (02f and 02g) making identical
changes to what this plan prescribed. No conflicts arose — the changes merged cleanly.

### No Extract-Method Applied

All candidates reviewed (StandingsController.standings() ~111 lines, SeasonPhaseController.save()
~60 lines) had complex early-return branching. Per CD-02: "Do NOT extract if it forces awkward
parameter lists." No extraction performed.

None — plan executed with expected scope sharing among concurrent agents.

## Verification

- Task 1: `./mvnw test` GREEN (50 tests, 0 failures, 1 pre-existing @Disabled skipped) after clean compile
- Task 2: Concurrent agent commits verified via git diff — changes identical to plan specification; full suite verification deferred to wave-merge orchestrator

## Self-Check: PASSED

- `PathTraversalGuard.java` modified at f4b8714: FOUND
- `20b3d34` (admin.controller commit): FOUND in git log
- SUMMARY.md created at correct path
