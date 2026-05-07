---
phase: 67-comment-cleanup-resweep
plan: 03
subsystem: tests-bdd-jacoco
tags:
  - cleanup
  - comment-policy
  - tests
  - bdd
  - jacoco
dependency_graph:
  requires:
    - 67-01-SUMMARY (production sweep — 22 files, 141 net comment lines stripped)
    - 67-02-SUMMARY (templates sweep — 4 files, 28 inline rewrites)
  provides:
    - "Test-code comment baseline: zero D-01..D-05 offenders in src/test/java repo-wide"
    - "Phase 67 final D-19 + D-20 gate signal — all 10 gates GREEN"
    - "BDD-marker preservation proof — 1,899 / 3,103 byte-equal between pre + post sweep"
  affects:
    - 50 source files (49 test files + 1 production JS file)
    - JaCoCo BUNDLE LINE coverage held at 0.8561 (≥ 0.82 floor)
tech-stack:
  added: []
  patterns:
    - "Per-file BDD-preserve check (grep before/after) per file edit — D-06 sacred invariant"
    - "Surgical phase-attribution prefix-strip with technical body retained (7 lines across 7 files)"
    - "Comments-only diff verified by non-comment-change scan post-edit"
key-files:
  created:
    - .planning/phases/67-comment-cleanup-resweep/67-03-SUMMARY.md
  modified:
    # Plan-listed 35 files (Tasks 1 + 2)
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
    - src/test/java/org/ctc/admin/controller/RaceControllerTest.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java
    - src/test/java/org/ctc/domain/service/RaceServiceTest.java
    - src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java
    - src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java
    - src/test/java/db/migration/V3MigrationTest.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java
    - src/test/java/org/ctc/domain/service/MatchdayServiceTest.java
    - src/test/java/org/ctc/dataimport/CsvImportServiceTest.java
    - src/test/java/org/ctc/admin/controller/TrackControllerTest.java
    - src/test/java/org/ctc/admin/controller/SeasonControllerTest.java
    - src/test/java/org/ctc/admin/controller/PlayoffControllerTest.java
    - src/test/java/org/ctc/admin/controller/CarControllerTest.java
    - src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java
    - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java
    - src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java
    - src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
    - src/test/java/org/ctc/admin/service/RaceGraphicServiceTest.java
    - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
    - src/test/java/org/ctc/domain/service/RaceLineupServiceTest.java
    - src/test/java/org/ctc/domain/service/MatchServiceTest.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java
    - src/test/java/org/ctc/dataimport/CsvImportControllerTest.java
    - src/test/java/org/ctc/admin/service/PowerRankingsGraphicServiceTest.java
    - src/test/java/org/ctc/domain/service/PlayoffServiceTest.java
    - src/test/java/org/ctc/domain/service/PlayoffSeedingServiceTest.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTransactionIT.java
    - src/test/java/org/ctc/domain/service/SwissPairingServiceTest.java
    - src/test/java/org/ctc/domain/service/RaceCalendarServiceTest.java
    - src/test/java/org/ctc/domain/service/FileStorageServiceTest.java
    - src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java
    - src/test/java/org/ctc/admin/controller/integration/SeasonPhaseControllerIT.java
    # Rule-3 scope expansion (15 files — RESEARCH.md inventory miss)
    - src/test/java/org/ctc/domain/service/MatchdayGeneratorServiceTest.java
    - src/test/java/org/ctc/admin/controller/StandingsControllerTest.java
    - src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java
    - src/test/java/org/ctc/admin/controller/TemplateEditorControllerTest.java
    - src/test/java/org/ctc/admin/service/SettingsGraphicServiceTest.java
    - src/test/java/org/ctc/dataimport/GoogleSheetsServiceTest.java
    - src/test/java/org/ctc/dataimport/ScorecardParserTest.java
    - src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java
    - src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryTest.java
    - src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java
    - src/test/java/org/ctc/domain/repository/SeasonPhaseGroupRepositoryIT.java
    - src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java
    - src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java
    - src/test/java/org/ctc/domain/service/PlayoffBracketViewServiceTest.java
    - src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java
    - src/main/resources/static/admin/js/timezone.js
decisions:
  - "Followed plan's instruction to use ONE final commit `style(67-03): test code comment cleanup re-sweep` covering all changes (rather than per-task commits). Plan Task 3 step 6 explicitly stages `git add src/test/java/` once and creates one commit at the end."
  - "Rule 3 scope expansion: 14 additional test files + 1 JS file added to scope so D-19 Gates 4 and 5 reach 0 phase-wide. RESEARCH.md inventory had missed tab-indented `// ===` decoration in MatchdayGeneratorServiceTest.java (6 lines) and `// --- helpers ---` short-decoration in 13 other test files + 3 lines in src/main/resources/static/admin/js/timezone.js. The plan and CONTEXT.md require these gates to be 0; the alternative (leaving residue, citing 'near-0 with documented exceptions' from VALIDATION.md) was rejected because Plan 67-03 acceptance criterion #5 specifies strict 0 for Gates 1-5. Expansion files received decoration-stripping only (no phase-attribution prefix-strip) since they were not in the plan's surgical-strip inventory."
  - "Two phase-attribution comments stripped in SeasonPhaseServiceTest.java (lines 236 + 406) instead of the single line 247 the plan listed. After Task-1 sweep shifted line numbers, walking the file revealed two `// Phase 60` comments matching D-19 Gate 1; both stripped to satisfy the gate."
metrics:
  duration: "~25 min (planning + research review + sweep + verify + summary)"
  completed: "2026-05-07T20:01:09Z"
  files_modified: 50
  net_lines_changed: "-291 (305 deletions, 14 insertions, mostly comment text rewrites)"
---

# Phase 67 Plan 03: Test Code Comment Cleanup Re-Sweep Summary

**One-liner:** Stripped ~291 net noise-comment lines (decoration separators + 7 phase-attribution prefix strips) from 49 test files + 1 production JS file with surgical preservation of all 1,899 / 3,103 BDD `// given` / `// when` / `// then` markers; phase-final `./mvnw verify` exit 0 with Tests run = 1,231 (Phase-66 baseline preserved) and JaCoCo BUNDLE LINE = 0.8561 (well above 0.82 floor).

## Scope

Plan 67-03 swept noise comments from `src/test/java` per CONTEXT.md D-01..D-05 (decoration / phase-attribution / WHAT-narration / stale TODO / migration history) while protecting the BDD test-marker invariant per D-06. Plan-listed scope was 35 test files. **Rule-3 scope expansion** (RESEARCH.md inventory miss) added 14 test files + 1 production JS file to satisfy D-19 Gates 4 and 5 phase-wide.

## What Was Done

### Decoration sweeps (~285 lines deleted)

- **Long-form decoration** (`^\s*//\s*[-=*#]{20,}` ≥ 20 chars): 64 lines across 7 files
  - `DriverSheetImportServiceTest.java` (40 lines, heaviest single offender repo-wide)
  - `DriverSheetImportServiceIT.java` (18)
  - `MatchdayGeneratorServiceTest.java` (6 — Rule 3 expansion)
- **Short 3-dash sectional** (`^\s*// ---`): 221 lines across 43 files

### Surgical phase-attribution prefix strips (7 lines across 7 files)

| File | Line (post-sweep) | Before | After |
|------|------------------:|--------|-------|
| `db/migration/V3MigrationTest.java` (Javadoc) | ~15 | `Asserts via INFORMATION_SCHEMA that the additive Phase 56 migration ran` | `Asserts via INFORMATION_SCHEMA that the additive V3 migration ran` |
| `db/migration/V3MigrationTest.java` (Javadoc) | ~23 | `Modelled on V5MigrationTest / V6MigrationTest (Phase 61 pattern).` | `Modelled on V5MigrationTest / V6MigrationTest.` |
| `e2e/GroupsSeasonE2ETest.java` | ~262 | `// Phase 60. Per D-15, the *race-result entry* must be UI-driven (STEP 7); ...` | `// the canonical MatchdayController only binds matchdays to the REGULAR phase. // The *race-result entry* must be UI-driven (STEP 7); ...` |
| `domain/service/SeasonPhaseServiceTest.java` | ~236 | `// Phase 60: update / delete / updateGroup / deleteGroup / assignTeamsToPhase` | `// update / delete / updateGroup / deleteGroup / assignTeamsToPhase` |
| `domain/service/SeasonPhaseServiceTest.java` | ~406 | `// Helpers (Phase 60 additions)` | `// Helpers` |
| `domain/service/StandingsServiceTest.java` | ~565 | `// Phase 58: phase/group/Buchholz/bridge tests (SVC-02, D-01, D-04, D-05, D-06)` | `// phase/group/Buchholz/bridge tests` |
| `domain/service/PlayoffServiceTest.java` | ~799 | `// Phase 58-05: D-19 auto-create + Pitfall 4 + @Deprecated M:N regression` | `// auto-create + @Deprecated M:N regression` |
| `domain/service/PlayoffSeedingServiceTest.java` | ~292 | `// Phase 58-05: D-15 Top-N from REGULAR phase standings + D-20 PhaseTeam side-effect` | `// Top-N from REGULAR phase standings + PhaseTeam side-effect` |
| `admin/controller/integration/SeasonPhaseControllerIT.java` | ~72 | `// Phase 61 gap-10: phase edit form must render non-empty option labels for // Phase Type, Layout, and Format dropdowns ...` | `// phase edit form must render non-empty option labels for // Phase Type, Layout, and Format dropdowns ...` |

The technical content "phase edit form must render non-empty option labels" is preserved (verified: `grep -c` returns 1).

## Per-file BDD-marker preservation (D-06 sacred)

For every one of the 50 files modified, the per-file BDD count was captured BEFORE and AFTER the edit and confirmed identical. Sample (the 5 heaviest):

| File | BDD-markers (before & after) |
|------|---:|
| `SiteGeneratorServiceTest.java` | 192 |
| `SeasonManagementServiceTest.java` | 137 |
| `PlayoffServiceTest.java` | 97 |
| `RaceControllerTest.java` | 88 |
| `DriverSheetImportServiceTest.java` | 81 |

**Phase-wide BDD baseline (PRE = POST):**
- Tab-prefixed canonical D-19 regex (`^	*// (given|when|then)`): **1,899** (target: ≥ 1,899) ✅
- Looser whitespace regex (`^[[:space:]]*//[[:space:]]*(given|when|then|when / then)`): **3,103** (target: ≥ 3,103) ✅

## All 7 D-19 Quantitative Grep Gates GREEN

```
Gate 1 (// Phase [56][0-9]):                    0   [expected 0]   ✅
Gate 2 (// per artifact):                       0   [expected 0]   ✅
Gate 3 (// gap-N):                              0   [expected 0]   ✅
Gate 4 (long decoration ≥20):                   0   [expected 0]   ✅
Gate 5 (short 3-dash sectional):                0   [expected 0]   ✅
Gate 6 (BDD canonical tab):                     1899   [expected ≥ 1899]   ✅
Gate 7 (BDD looser whitespace):                 3103   [expected ≥ 3103]   ✅
```

## All 3 D-20 Behavior Gates GREEN

```
Behavior Gate 1 (mvnw verify exit code):       0 (BUILD SUCCESS)         ✅
Behavior Gate 2 (Tests run):                   1231 (Phase-66 baseline)  ✅
Behavior Gate 3 (JaCoCo BUNDLE LINE):          0.8561 (≥ 0.82 floor)     ✅
```

`./mvnw verify` log excerpt:
```
[INFO] Tests run: 1231, Failures: 0, Errors: 0, Skipped: 4
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

## Plan 67-01 + 67-02 Regression Check

- Templates D-XX/Phase/Pitfall noise count: **0** (Plan 67-02 work intact)
- Whitelist preservation:
  - `Hero with YouTube background video` in `site/index.html`: count = 1 ✅
  - `phase edit form must render non-empty option labels` in `SeasonPhaseControllerIT.java`: count = 1 ✅
  - `// Phase 1: Create all car entities` (algorithm-step label) in `Gt7SyncService.java`: count = 1 (false-positive guard held — Gate 1 regex `[56][0-9]` correctly excludes single-digit `1`)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking gate failure] RESEARCH.md inventory missed 14 test files + 1 JS file**

- **Found during:** Task 2 verification (post-Task-2 D-19 gate check showed Gate 4 = 6, Gate 5 = 18)
- **Issue:** RESEARCH.md offender inventory was incomplete:
  - `MatchdayGeneratorServiceTest.java` had 6 tab-prefixed `// =====` long decoration lines (Gate 4)
  - 13 additional test files had `// --- helpers ---` style sectional decoration (Gate 5)
  - `src/main/resources/static/admin/js/timezone.js` had 3 `// --- Section ---` lines (Gate 5)
  - `StandingsControllerTest.java:168` had `// --- Phase 60 UI-05: ... ---` (Gate 5 + Phase-attribution)
- **Fix:** Expanded scope to include these 15 additional source files (14 tests + 1 JS). Applied decoration-stripping only (no phase-attribution prefix strips since these files were not in the surgical-strip inventory). For the JS file, rewrote the 3 lines to remove the `---` prefix while preserving the section-label semantic content (kept the function-context comment without the decoration markers).
- **Files modified (Rule 3):** see frontmatter `key-files.modified` "Rule-3 scope expansion" section
- **Commit:** `c72af74` (combined with main commit per plan's single-commit structure)

**2. [Rule 3 - Plan-line-number drift] SeasonPhaseServiceTest.java had 2 phase-attribution lines, not 1**

- **Found during:** Task 2 grep gate verification
- **Issue:** Plan flagged only `SeasonPhaseServiceTest.java:247`, but post-sweep the file contained 2 `// Phase 60` comments (lines 236 + 406 after Task-2 deletions): `// Phase 60: update / delete / ...` and `// Helpers (Phase 60 additions)`. Gate 1 (`// Phase [56][0-9]`) regex matches both.
- **Fix:** Stripped both. Technical content preserved on each line.
- **Commit:** `c72af74`

### Authentication Gates

None.

## Self-Check: PASSED

- Commit `c72af74` exists in `git log --oneline -5` ✅
- SUMMARY.md created at `.planning/phases/67-comment-cleanup-resweep/67-03-SUMMARY.md` ✅
- All 50 modified files staged + committed (51 files in commit including this SUMMARY.md is added in the next commit) ✅
- BDD-marker baselines unchanged (1,899 / 3,103) ✅
- All 7 D-19 grep gates pass ✅
- All 3 D-20 behavior gates pass ✅
- No accidental file deletions (`git diff --diff-filter=D HEAD~1 HEAD` empty) ✅

## Phase 67 Final Tally (D-24 — discretion-driven roll-up)

| Plan | Files modified | Net comment lines stripped | Commit |
|------|---------------:|---------------------------:|--------|
| 67-01 (production) | 22 | 141 | `afc5623` |
| 67-02 (templates) | 4 | 28 inline rewrites (0 attribution tokens remain) | `5502de4` + `fa4a352` |
| 67-03 (tests + Rule-3 expansion) | 50 (35 planned + 15 expansion) | ~291 | `c72af74` |
| **Phase 67 total** | **76** | **~460** | **3 style + 3 docs commits** |

Phase 67 closes with all 10 D-19 + D-20 gates GREEN, JaCoCo coverage held at 0.8561, and the BDD-marker invariant byte-equal preserved.

CI / pre-commit comment-noise guard automation deferred per CONTEXT.md `<deferred>`.
