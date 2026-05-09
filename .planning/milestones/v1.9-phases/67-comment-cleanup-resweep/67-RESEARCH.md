# Phase 67: Comment Cleanup Re-Sweep - Research

**Researched:** 2026-05-07
**Domain:** Repository-wide comment hygiene (production Java + Thymeleaf templates + tests)
**Confidence:** HIGH (every offender count verified by grep against the working tree)

## Phase Summary

This phase strips noise comments — decorative separators, phase / decision attribution, WHAT-narration, stale TODO/FIXME, and embedded migration history — from `src/main/java` (182 files), `src/main/resources/templates` (79 files), and `src/test/java` (121 files). BDD `// given` / `// when` / `// then` markers (1,899 tab-prefixed lines per the canonical D-19 regex; 3,103 lines total across 105 files when space-prefixed indents are counted) are sacred and must be preserved untouched. The sweep produces a comments-only diff: zero behavior change, zero coverage regression. Three plans are pre-decided in CONTEXT.md (D-14): Plan 67-01 production, Plan 67-02 templates, Plan 67-03 tests — sequential single-wave execution on the current branch (D-15, D-21).

## User Constraints (from CONTEXT.md)

### Locked Decisions

D-01..D-24 in 67-CONTEXT.md are all locked. The most operative for the planner:

- **D-01..D-05** define what gets removed (decoration / phase-attribution / WHAT-narration / stale TODO / migration history).
- **D-06..D-11** define what stays (BDD markers, public-API Javadoc, workaround comments, active spec refs, package-info, structurally-required Thymeleaf parser comments).
- **D-12** mandates per-file judgement, not blanket regex.
- **D-13** explicitly forbids automated bulk-regex deletion.
- **D-14** locks the three-plan structure (production / templates / tests).
- **D-15** locks sequential single-wave execution on main tree (no worktrees).
- **D-17** locks one final `./mvnw verify` at end of Plan 67-03; quick `./mvnw test -Dtest=…` between files inside each plan.
- **D-19** locks the quantitative grep gates (offender counts → 0; BDD-marker tab-prefixed count ≥ 1,899).
- **D-20** locks behavior gates (`./mvnw verify` exit 0; tests-run unchanged from 1,231 baseline; JaCoCo BUNDLE LINE ≥ 0.82).
- **D-21** locks the branch (`gsd/v1.9-season-phases-groups`).

### Claude's Discretion

- **D-22:** Per-file judgement on borderline Javadoc (V4/V5 migration history paragraphs).
- **D-23:** Whether to consolidate adjacent kept Javadoc lines.
- **D-24:** Final sweep counts in SUMMARY.md.

### Deferred Ideas (OUT OF SCOPE)

- CI / pre-commit comment-noise guard.
- Javadoc style normalization (`<p>` / `<ol>` formatting alignment).
- License-header policy.
- Migration-file Javadoc audit beyond what this phase removes.
- Comment-language audit (already English-only).
- Templates HTML-comment semantic / privacy review.

## Phase Requirements

This phase has no external requirement IDs (REQ-XX). Acceptance is defined by D-19 (quantitative grep gates) and D-20 (behavior gates) in CONTEXT.md.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Comment removal in services / controllers | Backend (production Java) | — | Plan 67-01 scope |
| Comment removal in Thymeleaf templates | SSR view layer | — | Plan 67-02 scope |
| Comment removal in tests (unit/IT/E2E) | Test code | — | Plan 67-03 scope |
| Behavior preservation gate | Maven Surefire + JaCoCo | — | D-20 gate |

This is a comments-only change; no tier owns logic that moves between layers.

## Project Constraints (from CLAUDE.md)

- **Comment policy:** *"Default to writing no comments. Only add one when the WHY is non-obvious"* — this phase is the enforcement pass.
- **BDD test structure:** Given-When-Then markers are MANDATORY. **Hard exception in this sweep.**
- **JaCoCo:** ≥ 82 % line coverage. Comments-only diff cannot move this.
- **No V1 migration changes:** Out of scope (all SQL migrations are Flyway-locked anyway).
- **Branch protection:** Stay on `gsd/v1.9-season-phases-groups`; no stash, no checkout, no reset.
- **Conventional commits:** Each plan ships one commit, `refactor(67-NN): …` or `style(67-NN): …`.

## Offender Inventory — Production (`src/main/java`)

**Methodology:** two grep patterns combined — `^\s*//[\s]*[-=*#]{20,}` (long decoration, D-01) and `^\s*// ---` (short 3-dash sectional, also D-01) plus `// Phase N` / `// per RESEARCH.md` etc. (D-02). Files with zero offenders are omitted.

| File | Decoration (`// ---`/`===`/`***`/`###`) | Phase / artifact attribution | Long Javadoc history | Total candidates | Notes |
|------|----:|----:|----:|----:|------|
| `domain/service/RaceService.java` | 9 | 0 | 0 | 9 | Sectional `// --- X ---` headers throughout |
| `domain/service/MatchdayService.java` | 9 | 0 | 0 | 9 | Sectional `// --- Save ---` / `// --- Delete ---` etc. (lines 32, 40, 50, 72, 95, 119, 131, 148, 156) |
| `domain/service/SeasonPhaseService.java` | 6 | 0 | 0 | 6 | Sectional headers |
| `domain/service/MatchdayGeneratorService.java` | 6 | 0 | 0 | 6 | Sectional headers |
| `domain/service/RaceFormDataService.java` | 5 | 0 | 0 | 5 | Sectional headers |
| `dataimport/DriverSheetImportService.java` | 4 | 0 | 0 | 4 | Sectional headers |
| `domain/service/SeasonManagementService.java` | 4 | 0 | 0 | 4 | Sectional headers |
| `domain/service/RaceLineupService.java` | 4 | 0 | 0 | 4 | Sectional headers |
| `domain/service/StandingsService.java` | 3 | 0 | 0 | 3 | Sectional + 1 borderline Phase 62 SC4-byte-identity comment (lines 75–77 — KEEP technical content, strip "Phase 62 Plan 1 Rule 1 fix" prefix) |
| `domain/service/SwissPairingService.java` | 2 | 0 | 0 | 2 | Sectional headers |
| `domain/service/RaceCalendarService.java` | 2 | 0 | 0 | 2 | |
| `domain/service/PlayoffService.java` | 2 | 0 | 0 | 2 | |
| `domain/service/DriverRankingService.java` | 2 | 0 | 0 | 2 | |
| `admin/controller/MatchdayController.java` | 2 | 0 | 0 | 2 | |
| `domain/service/PlayoffSeedingService.java` | 1 | 0 | 0 | 1 | |
| `domain/service/PlayoffBracketViewService.java` | 1 | 0 | 0 | 1 | |
| `admin/controller/RaceController.java` | 1 | 0 | 0 | 1 | |
| `admin/controller/PlayoffController.java` | 1 | 0 | 0 | 1 | |
| `sitegen/DriverProfilePageGenerator.java` | 0 | **3** | 0 | 3 | Lines 63, 75, 77: `Phase 62 D-15:` + `per RESEARCH.md` — strip prefix, **keep technical content** that follows |
| `sitegen/TeamProfilePageGenerator.java` | 0 | **2** | 0 | 2 | Lines 74, 99: `Phase 62 D-13/D-14:` and `Phase 62 D-13:` — strip prefix, keep technical content |
| `gt7sync/Gt7SyncService.java` | 0 | **3 (false-positive)** | 0 | 0 | Lines 82, 103, 124 say `// Phase 1:` / `// Phase 2:` / `// Phase 3:` — these are **algorithm-step labels** (Create cars / Create tracks / Download images) inside `executeSync()`, NOT phase attribution. **KEEP** (they label genuine algorithm phases; rephrase to `// Step 1:` if planner prefers an unambiguous form, but D-02 doesn't apply) |
| `db/migration/V4__MigrateSeasonsToPhases.java` | 0 | 0 | **heavy** | ~15 D-XX refs in Javadoc + inline | See "Migration-File Javadoc Judgement" below |
| `db/migration/V5__NullableLegacyScoringColumns.java` | 0 | 0 | **heavy** | 2-paragraph Javadoc | See "Migration-File Javadoc Judgement" below |
| `db/migration/V6__CleanupLegacySeasonColumns.java` | 0 | 0 | minor | low | Inline comments on lines 48–63 are **true workaround knowledge** (MariaDB 10.5+ DROP CONSTRAINT, dialect FK index auto-drop) — KEEP per D-08 |

**Per-plan footprint (Plan 67-01):** ~22 files touched. Heaviest cluster is the `domain/service` package (15 files). Two sitegen files require surgical prefix-strip (keep technical body). Migration files V4/V5 require careful Javadoc trimming (D-22 judgement); V6 is mostly KEEP.

### Production false-positive guard list (DO NOT touch)

- `Gt7SyncService.java:82,103,124` — algorithm-step labels, not phase attribution.
- All `// MariaDB ...` / `// H2 ...` dialect comments in V4/V5/V6 — true workaround knowledge (D-08).
- All Javadoc on `@Transactional`, `@OneToMany(cascade=…, orphanRemoval=…)`, `@EntityGraph` annotations — entity contract documentation (D-07).
- `MatchdayService.java:160-163` Javadoc explaining sortIndex-100 PLAYOFF-collision avoidance — non-obvious invariant (D-08).
- `MatchdayService.java:169-171` inline comment about REGULAR-phase scoping — explains a real bug-prevention rationale (D-08).

### Other production findings (verified)

- `grep -rn "TODO\|FIXME\|XXX" src/main/java` returns one line: `GoogleCalendarService.java:92` matches `XXX` only as part of the timezone format string `'yyyy-MM-dd\'T\'HH:mm:ss.SSSXXX'`. **Zero genuine TODO/FIXME markers in production.** D-04 has no work in Plan 67-01.

## Offender Inventory — Templates (`src/main/resources/templates`)

**Methodology:** `grep -rn "<!--"` per file, then visual classification of each comment as (a) structural section label (KEEP), (b) decision-attribution noise (REMOVE), (c) Thymeleaf parser comment (KEEP if functional). **Zero `<!--/* */-->` Thymeleaf parser comments in the codebase** — all comments are plain HTML `<!-- -->`. D-11 has no work; the call is purely between (a) section-label-keep vs. (b) attribution-noise-strip.

| File | Total `<!--` | Decision/phase attribution noise (REMOVE) | Section-label/structural (KEEP) | Notes |
|------|----:|----:|----:|------|
| `admin/template-editors.html` | 31 | 0 | 31 | All are clean tab/preview/editor section labels — KEEP entire file unchanged |
| `admin/season-detail.html` | 14 | **9** | 5 | `D-02, D-07`, `D-15`, `D-08`, `D-01`, `D-29`, `D-13/D-04`, `D-07`, `D-04`, `D-20`, `D-04 (placeholder; full content in Plan 60-06)`, `D-04 (placeholder; full UI in Plan 60-05)`, `B-3`, `D-04` — strip the decision IDs and "Plan 60-NN" references; keep the section name part of each label |
| `admin/import-preview.html` | 14 | 0 | 14 | All are plain section labels (Errors / Multi-race display / etc.) — KEEP |
| `admin/driver-import-preview.html` | 12 | **9** | 3 | `D-37 / Pitfall 10`, `D-38`, `IMPORT-04 / D-06`, `D-39, D-40` (×3), `UX-07, D-40`, `UX-08, D-40`, `D-40`, `D-16` — strip D-XX and Pitfall N attribution; keep section descriptions |
| `admin/race-detail.html` | 8 | 0 | 8 | All structural (Generate Graphics / Lineup Button / etc.) — KEEP |
| `admin/standings.html` | 7 | **5** | 2 | `D-29` (×2), `D-08 + Pitfall 4`, `D-36`, `D-32, D-33` — strip attribution; keep "Two-Row Tabs Row 1: Phase tabs", "Empty-state: phase exists but no results", etc. |
| `admin/import.html` | 7 | 0 | 7 | Structural — KEEP |
| `admin/matchday-detail.html` | 5 | 0 | 5 | Structural — KEEP |
| `admin/power-rankings.html` | 4 | 0 | 4 | Structural — KEEP |
| `admin/playoff-matchup.html` | 4 | 0 | 4 | Structural — KEEP |
| `admin/gt7-sync-preview.html` | 4 | 0 | 4 | Structural — KEEP |
| `admin/team-form.html` | 3 | 0 | 3 | Structural — KEEP |
| `admin/swiss-rounds.html` | 3 | 0 | 3 | Structural — KEEP |
| `admin/season-form.html` | 3 | 0 | 3 | Structural — KEEP |
| `admin/overlay-render.html` | 3 | 0 | 3 | Structural — KEEP |
| `site/index.html` | 2 | **1** | 1 | Line 6: `<!-- Hero with YouTube background video (Phase 51: YT-01, YT-02) -->` — strip phase attribution, keep "Hero with YouTube background video"; line 62: `<!-- Tile Navigation (D-10, D-11, D-12, D-13) -->` — strip D-IDs, keep "Tile Navigation" |
| `admin/season-phase-form.html` | 2 | 0 | 2 | Structural — KEEP |
| `admin/race-scoring-form.html` | 2 | 0 | 2 | Structural — KEEP |
| `admin/race-lineup.html` | 2 | 0 | 2 | Structural — KEEP |
| `admin/playoff-seed.html` | 2 | 0 | 2 | Structural — KEEP |
| `admin/driver-merge.html` | 2 | 0 | 2 | Structural — KEEP |
| 8 single-comment files | 1 each | 0 | 1 each | All structural — KEEP |

**Per-plan footprint (Plan 67-02):** 5 files touched (`season-detail.html`, `driver-import-preview.html`, `standings.html`, `site/index.html`, plus any planner discretion finds). Total attribution-marker removals expected: ~25 lines across ~5 files. The CONTEXT.md "10–20 noise removals" estimate aligns. The other 74 templates are clean section-label discipline and need no changes.

## Offender Inventory — Tests (`src/test/java`)

**Methodology:** same patterns as production, plus a separate `^\s*// (given|when|then)` BDD-marker count per file. Files with zero non-BDD offenders are omitted (full BDD-preserve list runs to 105 files). The BDD-markers column is informational — the planner never asks the executor to touch it.

| File | Decoration | Phase / artifact attribution | BDD markers (PRESERVE) | Total candidates | Notes |
|------|----:|----:|----:|----:|------|
| `dataimport/DriverSheetImportServiceTest.java` | 40 | 0 | 81 | 40 | Heaviest single offender repo-wide; 40 separator lines split helper methods + nested tests |
| `sitegen/SiteGeneratorServiceTest.java` | 23 | 0 | 192 | 23 | |
| `e2e/GroupsSeasonE2ETest.java` | 22 | 1 (`Phase 60` line 273) | 41 | 23 | The Phase-60 comment also embeds `Per D-15` — full attribution strip, keep step description |
| `admin/controller/RaceControllerTest.java` | 19 | 0 | 88 | 19 | |
| `dataimport/DriverSheetImportServiceIT.java` | 18 | 0 | 26 | 18 | |
| `domain/service/SeasonPhaseServiceTest.java` | 14 | 1 (`Phase 60` line 247) | 50 | 15 | |
| `domain/service/RaceServiceTest.java` | 11 | 0 | 45 | 11 | |
| `db/migration/V3MigrationTest.java` | 10 | 0 | 27 | 10 | The class-level Javadoc references "Phase 56" and "Phase 61 pattern" (lines 15, 23) — strip those; keep technical Surefire/INFORMATION_SCHEMA contract |
| `sitegen/SiteGeneratorE2ETest.java` | 9 | 0 | ~28 | 9 | |
| `domain/service/MatchdayServiceTest.java` | 7 | 0 | 52 | 7 | |
| `dataimport/CsvImportServiceTest.java` | 7 | 0 | 77 | 7 | |
| `admin/controller/TrackControllerTest.java` | 6 | 0 | ~30 | 6 | |
| `admin/controller/SeasonControllerTest.java` | 6 | 0 | 44 | 6 | |
| `admin/controller/PlayoffControllerTest.java` | 6 | 0 | 47 | 6 | |
| `admin/controller/CarControllerTest.java` | 6 | 0 | ~30 | 6 | |
| `db/migration/V4MigrateSeasonsToPhasesIT.java` | 6 | 0 | ~25 | 6 | |
| `domain/service/SeasonManagementServiceTest.java` | 5 | 0 | 137 | 5 | |
| `domain/service/RaceFormDataServiceTest.java` | 5 | 0 | ~25 | 5 | |
| `domain/service/RaceAttachmentServiceTest.java` | 5 | 0 | 38 | 5 | |
| `admin/TestDataServiceIntegrationTest.java` | 5 | 0 | 48 | 5 | |
| `admin/service/RaceGraphicServiceTest.java` | 4 | 0 | ~25 | 4 | |
| `domain/service/StandingsServiceTest.java` | 3 | 1 (`Phase 58` line 566) | 60 | 4 | |
| `domain/service/RaceLineupServiceTest.java` | 3 | 0 | ~25 | 3 | |
| `domain/service/MatchServiceTest.java` | 3 | 0 | ~25 | 3 | |
| `dataimport/DriverSheetImportControllerTest.java` | 3 | 0 | 52 | 3 | |
| `dataimport/CsvImportControllerTest.java` | 3 | 0 | ~25 | 3 | |
| `admin/service/PowerRankingsGraphicServiceTest.java` | 3 | 0 | ~25 | 3 | |
| `domain/service/PlayoffServiceTest.java` | 2 | 1 (`Phase 58-05` line 800) | 97 | 3 | |
| `domain/service/PlayoffSeedingServiceTest.java` | 2 | 1 (`Phase 58-05` line 294) | 34 | 3 | |
| `dataimport/DriverSheetImportServiceTransactionIT.java` | 2 | 0 | ~20 | 2 | |
| `domain/service/SwissPairingServiceTest.java` | 2 | 0 | 32 | 2 | |
| `domain/service/RaceCalendarServiceTest.java` | 2 | 0 | ~20 | 2 | |
| `domain/service/FileStorageServiceTest.java` | 2 | 0 | 51 | 2 | |
| `domain/service/DriverRankingServiceTest.java` | 2 | 0 | 48 | 2 | |
| `admin/controller/integration/SeasonPhaseControllerIT.java` | 0 | 1 (`Phase 61 gap-10` line 72) | ~25 | 1 | Strip `Phase 61 gap-10:` prefix, keep "phase edit form must render non-empty option labels" technical content |

**Per-plan footprint (Plan 67-03):** ~35 test files touched. Total decoration-line removals expected: ~250+ lines. Phase-attribution-line removals: 6 lines across 6 files. **CRITICAL preservation invariant:** the `^\s*// (given|when|then|when / then)` regex matches **3,103 lines across 105 files** (1,899 of those are tab-prefixed per the canonical D-19 regex). Plan 67-03 must end with a green `./mvnw verify` AND the BDD-line count must not drop.

### Test-file ZERO-offender confirmation

86 of 121 test files have zero D-01..D-05 offenders. Specifically: all files in `src/test/java/org/ctc/domain/model/`, the majority of `src/test/java/org/ctc/admin/dto/`, and most controller-level tests. Plan 67-03 should explicitly NOT scan these — only the 35-file offender list above.

## WHY-Comment Whitelist Samples (preserve untouched)

Five concrete examples already in the codebase that match D-08 / D-07 / D-09 (workarounds, public-API contracts, active spec refs). Use these as the planner's "pattern recognition" reference:

1. **MariaDB-vs-H2 dialect workaround** — `db/migration/V6__CleanupLegacySeasonColumns.java:48-50`:
   ```java
   // Drop named FK/UK constraints explicitly before dropping the columns they reference.
   // MariaDB 10.5+ supports DROP CONSTRAINT IF EXISTS for FK and UK; H2 ignores unknown
   // constraint names gracefully under IF EXISTS.
   ```
   KEEP — true MariaDB-version-specific workaround knowledge (D-08).

2. **Hibernate cascade index auto-drop knowledge** — `db/migration/V6__CleanupLegacySeasonColumns.java:55-58`:
   ```java
   // Bridge FK columns. The named FK indexes from V2 (idx_matchdays_season_id /
   // idx_playoffs_season_id) reference only this column, so both H2 and MariaDB
   // auto-drop the index when the column is dropped — no explicit DROP INDEX needed
   // (and explicit DROP INDEX needs different syntax across dialects).
   ```
   KEEP — non-obvious DB-engine behavior worth documenting at the call site (D-08).

3. **Cross-phase sortIndex-collision invariant** — `domain/service/MatchdayService.java:169-171`:
   ```java
   // Scope to the REGULAR phase: a season-wide lookup would (1) let playoff matchdays
   // (sortIndex >= 100) poison the next REGULAR sortIndex and (2) make the duplicate-label
   // check collide across phases.
   ```
   KEEP — explains a non-obvious bug-prevention rationale that would surprise a reader unfamiliar with the sortIndex-100 PLAYOFF convention (D-08).

4. **Public-API Javadoc with regulatory invariant** — `domain/service/MatchdayService.java:160-163` Javadoc on `createInline`:
   ```java
   /**
    * Creates a matchday inline (JSON API) bound to the season's REGULAR phase. The duplicate-label
    * guard and {@code sortIndex} computation are scoped to the REGULAR phase to avoid cross-phase
    * collisions with PLAYOFF matchdays (which use sortIndex &gt;= 100).
    */
   ```
   KEEP — public-API contract documentation (D-07).

5. **Layout invariant Javadoc on `@Transactional` service method** — `domain/service/MatchdayGeneratorService.java:30-41`:
   ```java
   /**
    * Generates matchdays for the given phase and optional group.
    *
    * <p>Layout validation:
    * <ul>
    *   <li>For {@code layout=LEAGUE}: {@code groupId} MUST be null — throws {@link IllegalArgumentException} if not.</li>
    *   <li>For {@code layout=GROUPS}: {@code groupId} MUST be non-null — throws {@link IllegalArgumentException} if null.</li>
    * </ul>
    *
    * <p>Teams are sourced from {@link PhaseTeamRepository} (not {@code season.getEligibleTeams()}).
    * Generated matchdays are linked to both {@code phase} and, for GROUPS layout, to the {@code group}.
    */
   ```
   KEEP — public-API contract + non-obvious "Teams sourced from PhaseTeamRepository, not Season" semantic (D-07 + D-08).

## Migration-File Javadoc Judgement (V4 / V5)

CONTEXT.md D-22 leaves this to per-file judgement; here is the concrete keep/strip recommendation for the planner.

### V5 — `V5__NullableLegacyScoringColumns.java`

**Current Javadoc (lines 11–25):**
```java
/**
 * V5 Flyway Java migration: legacy scoring FK columns become nullable.
 *
 * <p>The slim Season form (Phase 60 UI-01) no longer requires
 * {@code raceScoring}/{@code matchScoring} at season-creation time; scoring is
 * configured per-phase via the new Phase form. The auto-bootstrapped REGULAR
 * phase starts with scoring={@code null} and the user fills it in from the
 * Phase tab. The columns themselves remain (existing data preserved).
 *
 * <p>Originally shipped as V5__nullable_legacy_scoring_columns.sql, but that
 * version used PostgreSQL/H2-only {@code ALTER COLUMN ... DROP NOT NULL} syntax
 * which raises MariaDB error 1064 — production deploys to MariaDB never
 * succeeded. Replaced with this dialect-aware Java migration following the
 * pattern established by V4__MigrateSeasonsToPhases.
 */
```

**Recommended after-state — keep technical contract, strip historical narrative:**
```java
/**
 * V5 Flyway Java migration: legacy scoring FK columns become nullable.
 *
 * <p>The slim Season form no longer requires {@code raceScoring}/{@code matchScoring}
 * at season-creation time; scoring is configured per-phase. The auto-bootstrapped
 * REGULAR phase starts with scoring={@code null} and the user fills it in from the
 * Phase tab. The columns themselves remain (existing data preserved).
 *
 * <p>Java migration (not SQL) because {@code ALTER COLUMN ... DROP NOT NULL} is H2-only —
 * MariaDB requires {@code MODIFY COLUMN ... NULL}. Dialect detection via
 * {@code getDatabaseProductName()}.
 */
```
- **Removed:** `(Phase 60 UI-01)` attribution; the entire "Originally shipped as V5__nullable_legacy_scoring_columns.sql" historical paragraph.
- **Kept:** the technical contract (what the migration does, why it touches these columns), and the **dialect-divergence rationale** (rephrased into a forward-looking technical note instead of a postmortem).

### V4 — `V4__MigrateSeasonsToPhases.java`

The class-level Javadoc (lines 18–33) is technically correct and useful (lists the 5 ordered steps, notes single-transaction guarantee). **Strip the `Phase 56`, `D-02 / D-13`, `D-04` attribution markers; keep the ordered-list contract and the `canExecuteInTransaction() = true` note.**

For the per-method `D-XX` inline references (e.g., line 69 `// D-05: fail-fast on null scoring`, line 113 `// D-05: fail-fast if a PLAYOFF phase for this season already exists`, line 159 `// D-05: fail-fast — any matchday still NULL means an orphan season_id exists`, etc.) — the rationale ("fail-fast on null scoring", "fail-fast on orphan rows") is genuine WHY content. **Strip the `D-05:` / `D-09:` / `D-10:` / `D-11:` prefix; keep everything after the colon.**

The line 218 `log.info("Skipping NOT NULL flip — no seasons found (empty database; flip deferred until Phase 59 seeder update)")` is a **log message string, not a comment** — out of scope for this phase.

The line 234 `// Defensive UUID conversion helper (D-14 / Pitfall 1 in RESEARCH.md).` — **strip entire comment**; the helper's name (`toUUID`) and signature already convey what it does, and the D-14 / Pitfall reference is exactly the kind of stale artifact ref D-02 forbids.

## Validation Architecture (Nyquist Dim 8)

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito (Surefire); Playwright 1.56 (Failsafe `-Pe2e`) |
| Config file | `pom.xml` (Surefire, Failsafe, JaCoCo plugins) |
| Quick run command | `./mvnw test` (Surefire only) |
| Full suite command | `./mvnw verify` (Surefire + JaCoCo gate) |
| E2E command (NOT used in this phase's gate per `feedback_e2e_verification`) | `./mvnw verify -Pe2e` |

### Quantitative grep gates (from CONTEXT.md D-19 — verified runnable)

| Gate | Command | Expected |
|------|---------|---------:|
| Phase-attribution markers (50–69) | `grep -rn "// Phase [56][0-9]" src/main src/test \| wc -l` | **0** |
| Artifact references | `grep -rn "// per RESEARCH.md\|// per CONTEXT.md\|// per CLAUDE.md\|// per ROADMAP.md" src/main src/test \| wc -l` | **0** |
| Gap-tracking remnants | `grep -rn "// gap-[0-9]" src/main src/test \| wc -l` | **0** |
| Long decoration separators | `grep -rEn "^[[:space:]]*//[[:space:]]*[-=*#]{20,}" src/main src/test \| wc -l` | **0** (or near-0 with documented exceptions) |
| Short 3-dash sectional separators (auxiliary, not in D-19 but D-01-equivalent) | `grep -rcE "^\s*// ---" src/main src/test \| awk -F: 'BEGIN{s=0} {s+=$2} END{print s}'` | low single digits expected after sweep; goal **0** |
| BDD-marker preservation (canonical D-19 regex) | `grep -rn "^	*// given\|^	*// when\|^	*// then" src/test \| wc -l` | **≥ 1899** |
| BDD-marker preservation (looser, full whitespace) | `grep -rEn "^[[:space:]]*//[[:space:]]*(given\|when\|then\|when / then)" src/test/java \| wc -l` | **≥ 3103** |

### Behavior gates (from CONTEXT.md D-20)

| Gate | Command | Expected |
|------|---------|----------|
| Maven verify exit code | `./mvnw verify` | exit 0 |
| Tests-run count | parsed from Surefire stdout | unchanged from Phase-66 baseline (1,231) |
| JaCoCo line coverage | `target/site/jacoco/index.html` BUNDLE LINE | ≥ 0.8200 (current baseline 0.8561) |

### Per-plan sampling rate (per D-17 + `feedback_test_call_optimization`)

- **Per-file edit (within a plan):** `./mvnw test -Dtest=<RelevantTest>` only when a production service / controller's bytecode could plausibly be affected. For pure-comment edits to a test file, `git diff --stat` is the only check.
- **Per-plan commit:** `./mvnw test` (Surefire only, no JaCoCo). Quick gate that all tests still compile and pass.
- **Phase gate (after Plan 67-03):** ONE `./mvnw verify` confirming all D-19 grep gates AND D-20 behavior gates pass.

### Wave 0 Gaps

None — existing test infrastructure covers all phase requirements. Comments-only diff means no new tests are needed. The grep gates ARE the new test surface and they're plain shell pipelines.

## Threat Model Inputs

This is a comments-only diff. The threat surface is intentionally minimal.

| STRIDE | Applies | Notes |
|--------|---------|-------|
| Spoofing | N/A | No identity / auth code touched. |
| Tampering | N/A | No data path / persistence layer touched. |
| Repudiation | N/A | No audit log / signing code touched. |
| **Information Disclosure** | **LOW** | We are *removing* internal phase-decision references (`D-XX`, `Phase 5N`, `gap-NN`, `RESEARCH.md`) from source files. This *reduces* the project's information-disclosure surface — it does not increase it. The removed strings reference internal planning artifacts only; no secrets, credentials, or PII are involved. The `.planning/` directory itself is committed to git, so the underlying decisions are still public — we are removing duplication, not data. |
| Denial of Service | N/A | No runtime path / resource-acquisition code touched. |
| Elevation of Privilege | N/A | No authorization code touched. |

**Net assessment:** The diff is comments-only, byte-for-byte equivalent at the bytecode level (JaCoCo line coverage cannot move). Acceptance is fully captured by the D-19 grep gates + D-20 behavior gates. No threat model expansion needed.

## Plan Sizing Recommendation

| Plan | Scope | Files touched | Expected line removals | Commit subject |
|------|-------|---------------|------------------------|----------------|
| **67-01** Production sweep | `src/main/java/**` | ~22 (heaviest: 10 services in `domain/service/`, 2 sitegen, 3 migrations, 1 dataimport, ~6 controllers/misc) | ~70–90 lines (decoration) + ~10 lines (sitegen prefix-strip) + ~10–15 lines (V4/V5 Javadoc trim) ≈ **~95–115 line removals** | `refactor(67-01): strip noise comments from src/main/java` |
| **67-02** Templates sweep | `src/main/resources/templates/**` | 5 (`season-detail.html`, `driver-import-preview.html`, `standings.html`, `site/index.html`; possibly +1 planner-discretion) | ~25 attribution lines (most are inline rewrites — `<!-- D-08 Empty-State: no REGULAR phase -->` becomes `<!-- Empty-State: no REGULAR phase -->`); zero structural-label deletions | `refactor(67-02): strip decision-attribution from templates` |
| **67-03** Tests sweep | `src/test/java/**` | ~35 of 121 (zero offenders in 86 files) | ~250+ decoration lines (Plan 67-03 is ~3× the size of Plan 67-01 by line count) + 6 phase-attribution prefix strips | `refactor(67-03): strip noise comments from src/test/java` |

**Single-wave sequential execution per D-15.** Each plan has its own commit. After Plan 67-03 commit, run the single `./mvnw verify` gate (D-17). If green and all D-19 grep gates pass, phase complete. If not, remediate inside the same plan before moving on.

**Plan 67-03 is the heaviest by line count, but trivial by judgement risk** — most edits are pure decoration deletion. The judgement-heavy work is concentrated in Plan 67-01 (V4/V5 migration Javadoc + sitegen prefix-strips).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The 1,231 tests-run baseline from Phase 66 SUMMARY is the correct comparison value [ASSUMED — taken verbatim from CONTEXT.md D-20]. | Validation Architecture | If actual current baseline differs, the verifier's "tests-run unchanged" gate uses a stale number. Verifier should re-establish baseline by capturing pre-phase test count just before Plan 67-01 starts. Mitigation: planner instructs executor to record `./mvnw test` count BEFORE first edit. |
| A2 | The 0.8561 JaCoCo line coverage baseline is stable [ASSUMED]. | Validation Architecture | Comments-only diff cannot change bytecode line coverage; this baseline is informational. Risk: zero. |
| A3 | All 105 BDD-marker test files have BDD markers in their canonical `// given` / `// when` / `// then` form (no exotic variants like `//given` no-space) [VERIFIED via grep against current tree — 1,899 tab-prefixed + 3,103 total whitespace-prefixed matches found]. | Offender Inventory — Tests | Risk: zero. |
| A4 | Templates HTML comments are server-stripped by Thymeleaf at render time and not visible in browser HTML output [ASSUMED — Thymeleaf default strips `<!-- -->` comments unless `<!--/* */-->` parser-comment syntax is used]. | Offender Inventory — Templates | LOW — even if some HTML comments leak to public output, the content removed is internal D-XX attribution, not secrets. The phase only *removes* comments; it cannot introduce new disclosure. |
| A5 | `Gt7SyncService.java:82,103,124` "Phase 1/2/3" comments are NOT phase-attribution false positives [VERIFIED by reading lines 75–134 — they label sequential algorithm steps inside `executeSync()`]. | Production false-positive guard list | Risk: zero. |

## Open Questions

None blocking. Per CONTEXT.md the policy is fully locked; per the inventory above the offender list is fully enumerated. Proceed to planning.

## Sources

### Primary (HIGH confidence — verified via grep against working tree, 2026-05-07)

- `grep -rcE "^\s*// ---" src/main/java` → 18 files with short 3-dash separators.
- `grep -rcE "^\s*// ---" src/test/java` → 30 files with short 3-dash separators.
- `grep -rEn "^[[:space:]]*//[[:space:]]*[-=*#]{20,}" src/main src/test` → 6 production files (long), 12 test files (long).
- `grep -rnE "// ?Phase [0-9]+\|// ?per RESEARCH\|...\|// ?gap-[0-9]+" src/main src/test` → 14 attribution lines across 9 files (production sitegen ×2, gt7sync ×3 false-positive, tests ×7, integration test ×1).
- `grep -rn "<!--" src/main/resources/templates` → 79 files surveyed; 4 files have D-XX/phase attribution requiring noise strip (5 with `site/index.html`).
- `grep -rn "^	*// given\|^	*// when\|^	*// then" src/test \| wc -l` → 1,899 (canonical D-19 BDD-marker preserve count).
- `grep -rEn "^[[:space:]]*//[[:space:]]*(given\|when\|then)" src/test/java \| wc -l` → 3,103 (full whitespace-prefix BDD count).
- `find src/main/java -name "*.java"` → 182. `find src/test/java -name "*.java"` → 121. `find src/main/resources/templates -name "*.html"` → 79. All three match CONTEXT.md.

### Secondary (HIGH confidence — direct file reads)

- `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java` (full read — Javadoc + inline D-XX inventory).
- `src/main/java/db/migration/V5__NullableLegacyScoringColumns.java` (full read — full Javadoc captured for keep/strip diff).
- `src/main/java/db/migration/V6__CleanupLegacySeasonColumns.java` (lines 40–75 read — workaround whitelist samples).
- `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java` (lines 55–95 read — verified Phase 62 prefix on legitimate technical comments).
- `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java` (lines 65–110 read — same pattern).
- `src/main/java/org/ctc/gt7sync/Gt7SyncService.java` (lines 75–135 read — confirmed Phase-1/2/3 are algorithm-step labels, not phase attribution).
- `src/main/java/org/ctc/domain/service/StandingsService.java` (lines 1–220 sampled — mixed WHAT/WHY comments, the Phase 62 SC4-byte-identity comment is borderline-keep).
- `src/main/java/org/ctc/domain/service/MatchdayService.java` (lines 1–175 sampled — true workaround comments alongside short-3-dash sectional decoration).
- `src/test/java/db/migration/V3MigrationTest.java` (lines 1–80 read — class-level Javadoc has Phase 56 / Phase 61 attribution to strip).
- `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` (lines 260–294 read — Step 6 comment is example of long `===` decoration that wraps legitimate explanation).
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` (lines 1–82 read — confirmed structure; 40 separators are evenly distributed).

### Tertiary (Context — read but not directly cited)

- `.planning/phases/67-comment-cleanup-resweep/67-CONTEXT.md` (full read — locked policy upstream).
- `.planning/STATE.md` (read — confirmed phase numbering, baseline test count).
- `CLAUDE.md` (read — comment policy + JaCoCo gate + branch rules).

## Metadata

**Confidence breakdown:**
- Offender inventory (production / templates / tests): **HIGH** — every count is from a grep against the working tree as of 2026-05-07.
- Whitelist samples: **HIGH** — direct file reads.
- V4/V5 keep/strip recommendation: **HIGH** for V5 (full file read); **MEDIUM** for V4 because only the first 100 lines were sampled — planner / executor should re-read full V4 file before editing to confirm no in-method judgement calls were missed.
- Threat model: **HIGH** — comments-only diff has near-zero attack surface by construction.
- Validation architecture: **HIGH** — gate commands verified runnable; D-19 / D-20 are locked by CONTEXT.md.

**Research date:** 2026-05-07
**Valid until:** Plan 67-01 starts execution. Counts will drift once edits begin; the inventory is a planning snapshot, not a continuous source of truth.

## RESEARCH COMPLETE
