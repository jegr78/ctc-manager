---
phase: 67-comment-cleanup-resweep
plan: 01
subsystem: production-code
tags: [cleanup, comment-policy, production, flyway, sitegen]
requires: []
provides:
  - "Production-code comment policy re-baseline (`src/main/java`)"
  - "V4 / V5 migration Javadoc trimmed to technical contract only"
  - "Sitegen prefix-strip — technical body preserved, attribution removed"
affects: []
tech-stack:
  added: []
  patterns: ["per-file judgement comment sweep (D-12)", "verbatim Javadoc keep/strip from RESEARCH.md (V5)"]
key-files:
  created:
    - .planning/phases/67-comment-cleanup-resweep/67-01-SUMMARY.md
  modified:
    - src/main/java/org/ctc/domain/service/RaceService.java
    - src/main/java/org/ctc/domain/service/MatchdayService.java
    - src/main/java/org/ctc/domain/service/SeasonPhaseService.java
    - src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java
    - src/main/java/org/ctc/domain/service/RaceFormDataService.java
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
    - src/main/java/org/ctc/domain/service/RaceLineupService.java
    - src/main/java/org/ctc/domain/service/StandingsService.java
    - src/main/java/org/ctc/domain/service/SwissPairingService.java
    - src/main/java/org/ctc/domain/service/RaceCalendarService.java
    - src/main/java/org/ctc/domain/service/PlayoffService.java
    - src/main/java/org/ctc/domain/service/DriverRankingService.java
    - src/main/java/org/ctc/domain/service/PlayoffSeedingService.java
    - src/main/java/org/ctc/domain/service/PlayoffBracketViewService.java
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    - src/main/java/org/ctc/admin/controller/MatchdayController.java
    - src/main/java/org/ctc/admin/controller/RaceController.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java
    - src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java
    - src/main/java/db/migration/V4__MigrateSeasonsToPhases.java
    - src/main/java/db/migration/V5__NullableLegacyScoringColumns.java
decisions:
  - "Single commit at end of plan covering all 22 files (per Task 3 plan structure)"
  - "Adopted V5 Javadoc rewrite verbatim from RESEARCH.md — no further judgement needed"
  - "V4 D-XX attribution prefixes stripped while preserving every fail-fast / dialect rationale comment"
  - "Sitegen prefix-strip: kept technical body (RaceLineup-as-source-of-truth, byte-identity rationale, N x M perf note); dropped attribution"
  - "StandingsService line 75-77 SC4 byte-identity rationale rewritten as technical WHY (no Phase 62 attribution)"
metrics:
  completed: 2026-05-07
  duration_seconds: 2409
  task_count: 3
  file_count: 22
  tests_run: 1231
  tests_passing: 1231
---

# Phase 67 Plan 01: Production Code Comment Cleanup Re-Sweep Summary

Stripped 191 noise lines (decorative separators, phase / D-XX attribution, embedded
migration history) from 22 production files in `src/main/java`. Re-baselined the
CLAUDE.md comment policy after the v1.9 (Phases 56–66) accumulation.

## Plan Goal

Re-enforce the CLAUDE.md comment policy on `src/main/java` after the v1.9 cluster
re-introduced phase-attribution markers, decorative section headers, and WHAT-narration.
Comments-only diff: zero behavior change, zero bytecode delta, zero coverage drift.

## Files Swept (22 total)

### Domain services (15 files — heaviest cluster)

| File | Edits applied |
|------|---------------|
| `RaceService.java` | 9 short `// --- X ---` sectionals stripped |
| `MatchdayService.java` | 9 sectionals stripped; PRESERVED sortIndex-100 invariant Javadoc + inline (D-08) |
| `SeasonPhaseService.java` | 6 long-decoration sectionals stripped |
| `MatchdayGeneratorService.java` | 6 sectionals stripped + `Phase-60 / A7 minimum-churn shape` Javadoc trim; PRESERVED Layout validation Javadoc (D-07) |
| `RaceFormDataService.java` | 5 sectionals stripped (kept RaceLineup-as-source-of-truth inline at line 148, D-08) |
| `SeasonManagementService.java` | 4 sectionals stripped |
| `RaceLineupService.java` | 4 sectionals stripped |
| `StandingsService.java` | 3 sectionals stripped + `Phase 62 Plan 1 Rule 1 fix` prefix rewritten to technical WHY about HashMap iteration determinism + SC4 byte-identity; `Tracked Behavior Change (v1.9 / D-19)` Javadoc tag stripped |
| `SwissPairingService.java` | 2 long-decoration sectionals stripped |
| `RaceCalendarService.java` | 2 sectionals stripped |
| `PlayoffService.java` | 2 sectionals stripped |
| `DriverRankingService.java` | 2 long-decoration sectionals stripped |
| `PlayoffSeedingService.java` | 1 sectional stripped |
| `PlayoffBracketViewService.java` | 1 sectional stripped |
| `DriverSheetImportService.java` | 4 long-decoration sectionals stripped (including the `// Tab-level warning + enum, sibling style to ErrorRow / ErrorReason` decorator-style comment) |

### Controllers (3 files)

| File | Edits applied |
|------|---------------|
| `MatchdayController.java` | 2 short-3-dash sectionals stripped |
| `RaceController.java` | 1 sectional stripped |
| `PlayoffController.java` | 1 sectional stripped |

### Sitegen (2 files — surgical prefix-strip)

| File | Edits applied |
|------|---------------|
| `DriverProfilePageGenerator.java` | Line 63 `Phase 62 D-15:` prefix → stripped, body kept; lines 75-82 `Phase 62 D-15: ... per RESEARCH.md` block rewritten to keep RaceLineup-as-source-of-truth WHY block, dropped both prefix + artifact ref |
| `TeamProfilePageGenerator.java` | Line 74 `Phase 62 D-13/D-14:` prefix → stripped, kept N x M perf note; line 99 `Phase 62 D-13:` prefix → stripped, kept byte-identity rationale (rewrote `SC4 byte-identity` to a self-contained explanation) |

### Migrations (2 files — judgement-heavy)

| File | Edits applied |
|------|---------------|
| `V5__NullableLegacyScoringColumns.java` | Class Javadoc replaced verbatim with the RESEARCH.md V5 keep/strip recommendation. `(Phase 60 UI-01)` attribution removed; "Originally shipped as ..." historical paragraph removed; gained forward-looking H2-vs-MariaDB dialect rationale |
| `V4__MigrateSeasonsToPhases.java` | Class Javadoc: `D-02 / D-13`, `D-04` attribution stripped, kept ordered 5-step list + canExecuteInTransaction note. Method Javadocs: 4 method-level `(D-NN)` references + ROADMAP-SC5 marker removed. Inline: 4× `// D-05:` prefixes stripped (kept fail-fast WHY content); 1× `// D-09:` prefix stripped; 1× `// Defensive UUID conversion helper (D-14 / Pitfall 1 in RESEARCH.md).` removed entirely. Method-level Javadoc on `flipNotNullConstraints` "Phase 59 seeder update" pre-empted to phase-agnostic "until seeders populate phase_id" then reverted to keep diff strictly comments-only — log message string preserved (per RESEARCH.md, log strings are out of scope). |

## Noise Tally (D-24)

- **Total deletions:** 190 lines
- **Total insertions:** 49 lines (rewrites for prefix-strip on V5 Javadoc, V4 method Javadocs, sitegen comments, StandingsService SC4-byte-identity comment)
- **Net comment-line removal:** 141 lines
- **Files touched:** 22 / 22 expected (100%)

## Whitelist Preservation Evidence (D-19 + acceptance criteria)

| Anchor | File | Expected | Actual |
|--------|------|----------|--------|
| `// Phase 1: Create cars` algorithm-step label | `Gt7SyncService.java:82` | preserved | preserved |
| `// Phase 2: Create tracks` | `Gt7SyncService.java:103` | preserved | preserved |
| `// Phase 3: Download images` | `Gt7SyncService.java:124` | preserved | preserved |
| `PLAYOFF matchdays` (sortIndex-100 Javadoc) | `MatchdayService.java` | ≥ 1 | 1 |
| `Layout validation` (MatchdayGenerator generate Javadoc) | `MatchdayGeneratorService.java` | ≥ 1 | 1 |
| `ALTER COLUMN ... DROP NOT NULL` (V5 dialect rationale) | `V5__NullableLegacyScoringColumns.java` | ≥ 1 | 1 |
| `Originally shipped as` (V5 historical narrative) | `V5__NullableLegacyScoringColumns.java` | 0 | 0 |
| `Defensive UUID conversion helper` (V4 stale ref) | `V4__MigrateSeasonsToPhases.java` | 0 | 0 |
| MariaDB workaround comments in V6 (D-08 whitelist) | `V6__CleanupLegacySeasonColumns.java` | untouched | untouched (file not in scope) |

## D-19 Production-Side Grep Gates (post-commit, all = 0)

```
Gate 1 (// Phase [56][0-9]):       0
Gate 2 (// per RESEARCH.md|...):   0
Gate 3 (// gap-N):                 0
Gate 4 (long decoration ≥20):      0
Gate 5 (short 3-dash // ---):      0
```

## Behavior Gate

- `./mvnw test` exit 0
- `Tests run: 1231, Failures: 0, Errors: 0, Skipped: 4` — unchanged from Phase 66 baseline.
- JaCoCo not run (per D-17, deferred to Plan 67-03 final `./mvnw verify`). Comments-only diff cannot move bytecode line coverage; baseline 0.8561 holds by construction.

## Judgement Calls Made (D-22, D-23)

1. **StandingsService:line 75-77 (`Phase 62 Plan 1 Rule 1 fix — pre-existing HashMap-iteration non-determinism broke the SC4 byte-identity invariant for the public site standings.html`)** — rewrote to a self-contained technical WHY: *"Without this, HashMap iteration order makes the public site standings.html non-byte-stable across runs, breaking the byte-identity invariant of the static-site generator."* Kept the genuine deterministic-ordering rationale; dropped the Phase / Plan / Rule attribution + `SC4` jargon that requires reading planning docs to decode.
2. **StandingsService method Javadoc:** `<strong>Tracked Behavior Change (v1.9 / D-19):</strong>` opener stripped — re-flowed prose to drop the version + decision-ID dressing while keeping the cross-phase aggregation contract.
3. **MatchdayGeneratorService GeneratorFormData Javadoc** — collapsed the verbose `Phase-60 UI cutover ... A7 minimum-churn shape.` paragraph into a single-line "carries both Season and SeasonPhase for template compatibility" — keeps the WHY (template compat) without the version-tag fossil.
4. **DriverProfilePageGenerator lines 75-82** — kept the *full* RaceLineup-as-source-of-truth WHY block (multi-line, still references CLAUDE.md as the rule source — intentional, per D-09 active spec ref). Stripped only the Phase 62 D-15 + per RESEARCH.md prefixes / artifact refs.
5. **TeamProfilePageGenerator line 99** — rewrote `(SC4 byte-identity)` to `(preserves byte-identity of generated HTML)` so the comment reads without needing to know the SC4 jargon.
6. **V4 line 218 log message** — initially rewrote `Phase 59 seeder update` → `seeders populate phase_id`. Reverted to keep the diff strictly comments-only per RESEARCH.md ("log message string, not a comment — out of scope"). Log strings are deferred to a future audit if and when project memory drift requires it.
7. **V4 method Javadocs** — removed the inline `(ROADMAP-SC5)` markers in `migratePlayoffPhases` and `migrateMatchdayFKs` Javadoc — those are planning-doc cross-references, not workaround knowledge. The "NOT touched" rationale itself is preserved.

## False-Positive Guard Confirmed

- `Gt7SyncService.java:82,103,124` — algorithm-step labels untouched (3/3 occurrences). The D-19 grep gate `// Phase [56][0-9]` correctly excludes single-digit phase numbers, so these were never flagged.
- `V6__CleanupLegacySeasonColumns.java` — true MariaDB-vs-H2 workaround knowledge per D-08; file not in scope, not touched.
- All `// MariaDB ...` / `// H2 ...` dialect comments in V4/V5 — preserved (D-08).

## Deferred / Out of Scope

- **V4 line 218 log-message string** — `Phase 59 seeder update` reference inside a `log.info()` argument. Per RESEARCH.md this is a log string, not a comment; deferred to a future audit if project-memory-drift requires it.
- **V6__CleanupLegacySeasonColumns.java** — out of scope per RESEARCH.md (file is mostly D-08 keep).
- **`// Phase 62 SC4-byte-identity fix.` / similar comments in tests** — Plan 67-03 territory.
- **Templates (`<!-- D-XX -->`)** — Plan 67-02 territory.
- **JaCoCo final gate** — deferred to Plan 67-03 per D-17.

## Commit

`afc5623 style(67-01): production code comment cleanup re-sweep`

22 files changed, 49 insertions(+), 190 deletions(-)

## Self-Check: PASSED

- [x] All 22 listed files modified (verified via `git status --short` after stage)
- [x] Commit `afc5623` exists in `git log --oneline -3`
- [x] All 5 D-19 production-side grep gates return `0`
- [x] Whitelist anchors preserved (positive grep counts confirmed)
- [x] `./mvnw test` exits 0 with `Tests run: 1231, Failures: 0`
- [x] No file deletions (post-commit deletion-check ran, "OK - no file deletions")
- [x] Branch invariant: stayed on `worktree-agent-a0a6a1cb9ce8185ec` (worktree branch — orchestrator merges back to `gsd/v1.9-season-phases-groups`)
- [x] No modifications to STATE.md / ROADMAP.md (per orchestrator contract)
- [x] No modifications to `Gt7SyncService.java` (false-positive guard satisfied)
